package com.trekking.app;

import com.trekking.app.features.auth.model.IssuedTokens;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String COMPANIES = "/api/v1/companies";
    private static final String ADMIN = "/api/v1/admin/companies";
    private static final Path LICENSES = Path.of("target", "company-license-tests", UUID.randomUUID().toString());
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @DynamicPropertySource
    static void companyProperties(DynamicPropertyRegistry registry) {
        registry.add("app.company.license-directory", () -> LICENSES.toString());
    }

    @Test
    void adminCanFollowLicenseLinksReturnedByEveryReviewResponse() throws Exception {
        var owner = session(account("owner@example.invalid", "customer"));
        var admin = session(account("admin@example.invalid", "admin"));
        long company = create(owner, "TAX-LINKS");
        upload(company, pdf(), owner).andExpect(status().isOk());
        var detail = authGet(ADMIN + "/" + company, admin).andExpect(status().isOk()).andReturn();
        String detailUrl = json.readTree(detail.getResponse().getContentAsString()).get("licenseUrl").asText();
        assertThat(detailUrl).isEqualTo(ADMIN + "/" + company + "/license");
        authGet(detailUrl, admin).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        var list = authGet(ADMIN, admin).andExpect(status().isOk()).andReturn();
        String listUrl = json.readTree(list.getResponse().getContentAsString()).get("items").get(0).get("licenseUrl").asText();
        authGet(listUrl, admin).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        var decision = decision(company, "approve", "Evidence verified", admin).andExpect(status().isOk()).andReturn();
        String decisionUrl = json.readTree(decision.getResponse().getContentAsString()).get("licenseUrl").asText();
        authGet(decisionUrl, admin).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        authGet(COMPANIES + "/" + company, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseUrl").value(COMPANIES + "/" + company + "/license"));
    }

    @Test
    void companyRegistrationIsAtomicAndPreservesExistingRoles() throws Exception {
        long customer = account("customer@example.invalid", "customer", "support_staff");
        var token = session(customer);
        var body = new java.util.HashMap<>(profile("tax-001"));
        body.put("status", "approved");
        body.put("approvedBy", customer);
        body.put("licenseFileUrl", "https://attacker.example.invalid/fake.pdf");
        var result = authPost(COMPANIES, body, token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.taxCode").value("TAX-001"))
                .andExpect(jsonPath("$.approvedBy").isEmpty())
                .andExpect(jsonPath("$.licenseAvailable").value(false))
                .andExpect(jsonPath("$.licenseFileUrl").doesNotExist()).andReturn();
        long company = json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ? order by role", String.class, customer))
                .containsExactly("company_manager", "customer", "support_staff");
        assertThat(jdbc.queryForObject("select member_role from trekking.company_members where company_id = ? and account_id = ?", String.class,
                company, customer)).isEqualTo("owner");
        assertThat(auditCount(company)).isEqualTo(1);
        authGet(COMPANIES + "/" + company, token).andExpect(status().isOk());
        authGet(COMPANIES, token).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(company));
    }

    @Test
    void anonymousAndIneligibleActorsCannotRegister() throws Exception {
        mvc.perform(post(COMPANIES).contentType("application/json").content(json.writeValueAsString(profile("TAX-1"))))
                .andExpect(status().isUnauthorized());
        long admin = account("admin@example.invalid", "admin");
        authPost(COMPANIES, profile("TAX-1"), session(admin)).andExpect(status().isForbidden());
        long customer = account("customer@example.invalid", "customer");
        var token = session(customer);
        jdbc.update("update trekking.app_accounts set email_verified_at = null where id = ?", customer);
        authPost(COMPANIES, profile("TAX-1"), token).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from trekking.companies", Integer.class)).isZero();
    }

    @Test
    void duplicateTaxCodeAndInvalidProfilesAreRejectedWithoutPartialMemberships() throws Exception {
        long first = account("first@example.invalid", "customer");
        long second = account("second@example.invalid", "customer");
        var firstToken = session(first);
        var secondToken = session(second);
        create(firstToken, "TAX-1");
        authPost(COMPANIES, profile("tax-1"), secondToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_TAX_CODE_EXISTS"));
        var bad = new java.util.HashMap<>(profile("TAX-2"));
        bad.put("contactEmail", "invalid-email");
        authPost(COMPANIES, bad, secondToken).andExpect(status().isBadRequest());
        bad.put("contactEmail", "contact@example.invalid");
        bad.put("name", "  ");
        authPost(COMPANIES, bad, secondToken).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_members where account_id = ?", Integer.class, second)).isZero();
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, second)).containsExactly("customer");
    }

    @Test
    void listsAndReadsStayWithinActiveMembershipAndCurrentRole() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long outsider = account("outsider@example.invalid", "customer", "company_manager");
        long admin = account("admin@example.invalid", "admin");
        var token = session(owner);
        var other = session(outsider);
        long company = create(token, "TAX-1");
        create(other, "TAX-2");
        authGet(COMPANIES + "?size=1", token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(company));
        authGet(COMPANIES + "/" + company, other).andExpect(status().isNotFound());
        authPut(COMPANIES + "/" + company, profile("TAX-1"), other).andExpect(status().isNotFound());
        authGet(COMPANIES + "/" + company, session(admin)).andExpect(status().isNotFound());
        jdbc.update("update trekking.company_members set active = false where company_id = ?", company);
        authGet(COMPANIES + "/" + company, token).andExpect(status().isNotFound());
        authGet(COMPANIES, token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("update trekking.company_members set active = true where company_id = ?", company);
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'company_manager'", owner);
        authGet(COMPANIES + "/" + company, token).andExpect(status().isNotFound());
        authGet(COMPANIES, token).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void activeManagerMayEditButBodyCannotOverrideApprovalOrLicense() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long manager = account("manager@example.invalid", "company_manager");
        long company = create(session(owner), "TAX-1");
        membership(company, manager, "manager", true);
        var body = new java.util.HashMap<>(profile("TAX-1"));
        body.put("name", "Updated company");
        body.put("status", "approved");
        body.put("licenseFileUrl", "../private.pdf");
        authPut(COMPANIES + "/" + company, body, session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated company"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.licenseAvailable").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "company_manager", "guide", "support_staff", "content_staff", "accountant"})
    void allNonAdminRolesAreDeniedReview(String role) throws Exception {
        long actor = account("actor@example.invalid", role);
        var token = session(actor);
        authGet(ADMIN, token).andExpect(status().isForbidden());
        authGet(ADMIN + "/9999", token).andExpect(status().isForbidden());
        decision(9999, "approve", "Evidence checked", token).andExpect(status().isForbidden());
    }

    @Test
    void approvalRequiresEvidenceReasonAndRecordsServerActorTime() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var ownerToken = session(owner);
        var adminToken = session(admin);
        long company = create(ownerToken, "TAX-1");
        decision(company, "approve", "Evidence checked", adminToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_LICENSE_REQUIRED"));
        upload(company, pdf(), ownerToken).andExpect(status().isOk());
        decision(company, "approve", "  ", adminToken).andExpect(status().isBadRequest());
        decision(company, "approve", "  Verified business  ", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvedBy").value(admin))
                .andExpect(jsonPath("$.approvedAt").isString())
                .andExpect(jsonPath("$.decisionReason").value("Verified business"));
        var audit = jdbc.queryForMap("select actor_id, reason, details::text from trekking.audit_logs where action='COMPANY_APPROVED'");
        assertThat(audit.get("actor_id")).isEqualTo(admin);
        assertThat(audit.get("reason")).isEqualTo("Verified business");
        assertThat(json.readTree(audit.get("details").toString()).get("before").get("status").asText()).isEqualTo("pending");
        decision(company, "approve", "Repeated", adminToken).andExpect(status().isConflict());
        decision(company, "reject", "Wrong transition", adminToken).andExpect(status().isConflict());
        authGet(ADMIN + "?status=approved", adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void approvedProfileAndEvidenceEditsRequireReviewAgain() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var ownerToken = session(owner);
        var adminToken = session(admin);
        long company = create(ownerToken, "TAX-1");
        upload(company, pdf(), ownerToken).andExpect(status().isOk());
        decision(company, "approve", "Approved", adminToken).andExpect(status().isOk());
        authPut(COMPANIES + "/" + company, profile("TAX-1"), ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.approvedBy").isEmpty()).andExpect(jsonPath("$.approvedAt").isEmpty());
        decision(company, "approve", "Approved again", adminToken).andExpect(status().isOk());
        upload(company, pdf(), ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void rejectionCanBeEditedAndResubmittedButSuspensionCannot() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var ownerToken = session(owner);
        var adminToken = session(admin);
        long company = create(ownerToken, "TAX-1");
        decision(company, "suspend", "Wrong transition", adminToken).andExpect(status().isConflict());
        decision(company, "reject", "Missing evidence", adminToken).andExpect(status().isOk());
        authPut(COMPANIES + "/" + company, profile("TAX-1"), ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));
        upload(company, pdf(), ownerToken).andExpect(status().isOk());
        authPost(COMPANIES + "/" + company + "/resubmit", Map.of(), ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending")).andExpect(jsonPath("$.decisionReason").isEmpty());
        authPost(COMPANIES + "/" + company + "/resubmit", Map.of(), ownerToken).andExpect(status().isConflict());
        decision(company, "approve", "Valid now", adminToken).andExpect(status().isOk());
        decision(company, "suspend", "Violation", adminToken).andExpect(status().isOk());
        authPut(COMPANIES + "/" + company, profile("TAX-1"), ownerToken).andExpect(status().isConflict());
        authPost(COMPANIES + "/" + company + "/resubmit", Map.of(), ownerToken).andExpect(status().isConflict());
        upload(company, pdf(), ownerToken).andExpect(status().isConflict());
        authGet(COMPANIES + "/" + company, ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("suspended"));
    }

    @Test
    void evidenceUploadValidatesContentAndDownloadsRequireScope() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long outsider = account("outsider@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var token = session(owner);
        var otherToken = session(outsider);
        long company = create(token, "TAX-1");
        upload(company, new MockMultipartFile("file", "fake.pdf", "application/pdf", "not-pdf".getBytes()), token)
                .andExpect(status().isBadRequest());
        upload(company, new MockMultipartFile("file", "fake.html", "text/html", PDF), token).andExpect(status().isBadRequest());
        upload(company, new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]), token)
                .andExpect(status().isBadRequest());
        upload(company, pdf(), otherToken).andExpect(status().isNotFound());
        upload(company, pdf(), token).andExpect(status().isOk()).andExpect(jsonPath("$.licenseAvailable").value(true))
                .andExpect(jsonPath("$.licenseUrl").value(COMPANIES + "/" + company + "/license"));
        authGet(COMPANIES + "/" + company + "/license", otherToken).andExpect(status().isNotFound());
        mvc.perform(get(COMPANIES + "/" + company + "/license")).andExpect(status().isUnauthorized());
        authGet(COMPANIES + "/" + company + "/license", token).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
        authGet(ADMIN + "/" + company + "/license", session(admin)).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        String stored = jdbc.queryForObject("select license_file_url from trekking.companies where id=?", String.class, company);
        assertThat(stored).matches("[0-9a-f-]{36}\\.pdf").doesNotContain("..", "/", "http");
    }

    @Test
    void paginationStatusAndPathInputsUseBoundedErrorContract() throws Exception {
        long actor = account("actor@example.invalid", "customer", "admin");
        var token = session(actor);
        for (String query : List.of("?page=-1", "?size=0", "?size=101", "?page=bad")) {
            authGet(COMPANIES + query, token).andExpect(status().isBadRequest());
            authGet(ADMIN + query, token).andExpect(status().isBadRequest());
        }
        authGet(ADMIN + "?status=bogus", token).andExpect(status().isBadRequest());
        authGet(ADMIN + "/99999", token).andExpect(status().isNotFound());
        authGet(COMPANIES + "/bad", token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void auditFailureRollsBackCreationMembershipAndRole() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        var token = session(owner);
        jdbc.execute("alter table trekking.audit_logs add constraint qa_company_creation check (action <> 'COMPANY_CREATED')");
        try {
            authPost(COMPANIES, profile("TAX-1"), token).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("select count(*) from trekking.companies", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from trekking.company_members", Integer.class)).isZero();
            assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, owner)).containsExactly("customer");
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_company_creation");
        }
    }

    @Test
    void auditFailureRollsBackDecisionAndCleansNewEvidenceFile() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var ownerToken = session(owner);
        var adminToken = session(admin);
        long company = create(ownerToken, "TAX-1");
        long beforeFiles = fileCount();
        jdbc.execute("alter table trekking.audit_logs add constraint qa_company_upload check (action <> 'COMPANY_LICENSE_UPLOADED')");
        try {
            upload(company, pdf(), ownerToken).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("select license_file_url from trekking.companies where id=?", String.class, company)).isNull();
            assertThat(fileCount()).isEqualTo(beforeFiles);
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_company_upload");
        }
        upload(company, pdf(), ownerToken).andExpect(status().isOk());
        jdbc.execute("alter table trekking.audit_logs add constraint qa_company_decision check (action <> 'COMPANY_APPROVED')");
        try {
            decision(company, "approve", "Checked", adminToken).andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForObject("select status from trekking.companies where id=?", String.class, company)).isEqualTo("pending");
            assertThat(jdbc.queryForObject("select approved_by from trekking.companies where id=?", Long.class, company)).isNull();
            assertThat(auditCount(company)).isEqualTo(2);
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_company_decision");
        }
    }

    @Test
    void concurrentModerationProducesExactlyOneDecisionAndAudit() throws Exception {
        long owner = account("owner@example.invalid", "customer");
        long admin = account("admin@example.invalid", "admin");
        var token = session(owner);
        var reviewer = session(admin);
        long company = create(token, "TAX-1");
        upload(company, pdf(), token).andExpect(status().isOk());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (String action : List.of("approve", "reject")) {
                futures.add(executor.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    return decision(company, action, "Concurrent review", reviewer).andReturn().getResponse().getStatus();
                }));
            }
            gate.countDown();
            assertThat(List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs where entity_type='company' and entity_id=? "
                + "and action in ('COMPANY_APPROVED','COMPANY_REJECTED')", Integer.class, company)).isEqualTo(1);
    }

    private Map<String, Object> profile(String taxCode) {
        return Map.of("name", "Trekking Company", "taxCode", taxCode, "representativeName", "Company Owner",
                "contactEmail", "contact@example.invalid", "contactPhone", "0900000000", "address", "Company address", "description", "Tour operator");
    }

    private long create(IssuedTokens token, String taxCode) throws Exception {
        var result = authPost(COMPANIES, profile(taxCode), token).andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions decision(long company, String action, String reason, IssuedTokens token) throws Exception {
        return authPost(ADMIN + "/" + company + "/decision", Map.of("decision", action, "reason", reason), token);
    }

    private ResultActions upload(long company, MockMultipartFile file, IssuedTokens token) throws Exception {
        return mvc.perform(multipart(COMPANIES + "/" + company + "/license").file(file)
                .header("Authorization", "Bearer " + access(token)));
    }

    private MockMultipartFile pdf() { return new MockMultipartFile("file", "license.pdf", "application/pdf", PDF); }

    private int auditCount(long company) {
        return jdbc.queryForObject("select count(*) from trekking.audit_logs where entity_type='company' and entity_id=?", Integer.class, company);
    }

    private long fileCount() throws Exception {
        if (!Files.exists(LICENSES)) return 0;
        try (var files = Files.list(LICENSES)) { return files.count(); }
    }
}
