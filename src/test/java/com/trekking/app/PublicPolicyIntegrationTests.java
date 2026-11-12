package com.trekking.app;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.public-policies.privacy-policy-code=EXISTING_PRIVACY")
@AutoConfigureMockMvc
class PublicPolicyIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String PUBLIC = "/api/v1/public/policies/";
    private static final String ADMIN = "/api/v1/admin/policies";

    @ParameterizedTest
    @ValueSource(strings = {"BOOKING_GUIDE", "REFUND_POLICY", "WEBSITE_TERMS", "PRIVACY_POLICY", "LIABILITY_WAIVER"})
    void guestsCanReadOnlyThePublicDocumentProjection(String code) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, code.equals("PRIVACY_POLICY") ? "EXISTING_PRIVACY" : code);
        version(admin, policy, 1, OffsetDateTime.now().minusDays(1), true);
        mvc.perform(get(PUBLIC + code)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.content").value("Published content 1"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.approvedBy").doesNotExist())
                .andExpect(jsonPath("$.policyId").doesNotExist())
                .andExpect(jsonPath("$.revocationReason").doesNotExist());
    }

    @Test
    void publicRouteDoesNotExposeOtherActivePoliciesOrTheAdminList() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long internal = policy(admin, "INTERNAL_NOTES");
        version(admin, internal, 1, OffsetDateTime.now().minusDays(1), true);
        mvc.perform(get(PUBLIC + "INTERNAL_NOTES")).andExpect(status().isNotFound());
        mvc.perform(get(PUBLIC + "EXISTING_PRIVACY")).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN + "/available")).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN + "/" + internal + "/versions")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted", "draft", "future", "future-approval", "revoked"})
    void unavailableDocumentsAreNotPublic(String state) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, "BOOKING_GUIDE");
        long version = version(admin, policy, 1,
                state.equals("future") ? OffsetDateTime.now().plusDays(2) : OffsetDateTime.now().minusDays(1),
                !state.equals("draft"), state.equals("future-approval"));
        if (state.equals("inactive")) jdbc.update("update trekking.policies set active=false where id=?", policy);
        if (state.equals("deleted")) jdbc.update("update trekking.policies set deleted_at=current_timestamp where id=?", policy);
        if (state.equals("revoked")) revoke(admin, version);
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLIC_POLICY_NOT_FOUND"));
    }

    @Test
    void scheduledAndCancelledVersionsDoNotLeakAndRevokingCurrentDoesNotRestoreOldTerms() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, "BOOKING_GUIDE");
        version(admin, policy, 1, OffsetDateTime.now().minusDays(3), true);
        long current = version(admin, policy, 2, OffsetDateTime.now().minusDays(1), true);
        long scheduled = version(admin, policy, 3, OffsetDateTime.now().plusDays(2), true);
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2));
        revoke(admin, scheduled);
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2));
        revoke(admin, current);
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "guide"})
    void nonAdminsCanReadPublicDocumentsButCannotManagePolicies(String role) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long caller = account("caller@example.invalid", role);
        long policy = policy(admin, "BOOKING_GUIDE");
        long version = version(admin, policy, 1, OffsetDateTime.now().minusDays(1), true);
        var token = session(caller);
        authGet(PUBLIC + "BOOKING_GUIDE", token).andExpect(status().isOk());
        authGet(ADMIN, token).andExpect(status().isForbidden());
        authGet(ADMIN + "/available", token).andExpect(status().isForbidden());
        authPost(ADMIN, Map.of("code", "NEW", "title", "New", "kind", "general"), token)
                .andExpect(status().isForbidden());
        authPost(ADMIN + "/" + policy + "/versions",
                Map.of("content", "Changed", "effectiveFrom", OffsetDateTime.now().toString()), token)
                .andExpect(status().isForbidden());
        mvc.perform(patch(ADMIN + "/" + policy + "/status").header("Authorization", "Bearer " + access(token))
                .contentType("application/json").content("{\"active\":false,\"reason\":\"No permission\"}"))
                .andExpect(status().isForbidden());
        authPost(ADMIN + "/" + policy + "/versions/" + version + "/revoke", Map.of("reason", "No permission"), token)
                .andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN + "/" + policy).header("Authorization", "Bearer " + access(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestWriteMethodsStayProtected() throws Exception {
        String body = "{\"code\":\"X\",\"title\":\"X\",\"kind\":\"general\"}";
        mvc.perform(post(ADMIN).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(PUBLIC + "BOOKING_GUIDE").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(PUBLIC + "BOOKING_GUIDE").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(PUBLIC + "BOOKING_GUIDE").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanStillPublishAndImmediatelyWithdrawThePublicDocument() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long policy = policy(admin, "BOOKING_GUIDE");
        var token = session(admin);
        authPost(ADMIN + "/" + policy + "/versions",
                Map.of("content", "Approved guide", "effectiveFrom", OffsetDateTime.now().minusMinutes(1).toString()), token)
                .andExpect(status().isCreated());
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Approved guide"));
        mvc.perform(patch(ADMIN + "/" + policy + "/status").header("Authorization", "Bearer " + access(token))
                .contentType("application/json").content("{\"active\":false,\"reason\":\"Withdraw document\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(PUBLIC + "BOOKING_GUIDE")).andExpect(status().isNotFound());
    }

    private long policy(long admin, String code) {
        return jdbc.queryForObject("insert into trekking.policies(code,title,kind,created_by,active) values (?,?,'general',?,true) returning id",
                Long.class, code, "Document " + code, admin);
    }

    private long version(long admin, long policy, int number, OffsetDateTime effective, boolean approved) {
        return version(admin, policy, number, effective, approved, false);
    }

    private long version(long admin, long policy, int number, OffsetDateTime effective, boolean approved, boolean futureApproval) {
        return jdbc.queryForObject("insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at) values (?,?,?,?,?,?) returning id",
                Long.class, policy, number, "Published content " + number, effective,
                approved ? admin : null,
                approved ? (futureApproval ? OffsetDateTime.now().plusDays(2) : OffsetDateTime.now().minusDays(4)) : null);
    }

    private void revoke(long admin, long version) {
        jdbc.update("insert into trekking.policy_version_revocations(policy_version_id,revoked_by,revoked_at,reason) values (?,?,current_timestamp,'Withdraw')",
                version, admin);
    }
}
