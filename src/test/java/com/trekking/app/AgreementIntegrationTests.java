package com.trekking.app;

import com.trekking.app.features.auth.model.IssuedTokens;
import com.trekking.app.features.auth.enums.OtpPurpose;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AgreementIntegrationTests extends AccountAuthorizationTestSupport {
    @MockitoBean JavaMailSender agreementMail;
    long admin;
    long owner;
    long company;
    long policy;
    long version;
    IssuedTokens ownerToken;
    IssuedTokens adminToken;

    @BeforeEach
    void agreementFixtures() {
        admin = account("admin@example.invalid", "admin");
        owner = account("owner@example.invalid", "company_manager");
        company = company(admin, "Agreement Company");
        membership(company, owner, "owner", true);
        policy = policy("global", null, "cooperation", true);
        version = version(policy, 1, true, "-1 day", true);
        ownerToken = session(owner);
        adminToken = session(admin);
    }

    @Test
    void adminPublishesExplicitApprovedVersionAndPartnersReadOnlyScopedPolicy() throws Exception {
        var created = authPost("/api/v1/admin/policies", Map.of("code", "new-policy", "title", "New terms", "companyId", company), adminToken)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("cooperation")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        authPost("/api/v1/admin/policies/" + id + "/versions", publishBody(), ownerToken).andExpect(status().isForbidden());
        authPost("/api/v1/admin/policies/" + id + "/versions", publishBody(), adminToken)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.approvedBy").value(admin)).andExpect(jsonPath("$.platformFeePercent").value(7.25));
        authGet(base() + "/agreement-policies", ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        authGet(base() + "/agreement-policies", adminToken).andExpect(status().isNotFound());
        authPost("/api/v1/admin/policies/" + id + "/versions", Map.of("content", "Missing finance"), adminToken)
                .andExpect(status().isBadRequest());
    }

    @Test
    void eligibilityRejectsInactiveForeignWrongKindFutureUnapprovedAndLegacyFinancialTerms() throws Exception {
        long foreign = company(admin, "Foreign");
        for (long excluded : List.of(policy("inactive", null, "cooperation", false), policy("foreign", foreign, "cooperation", true),
                policy("payment", null, "payment", true))) version(excluded, 1, true, "-1 day", true);
        version(policy("future", null, "cooperation", true), 1, true, "1 day", true);
        version(policy("draft", null, "cooperation", true), 1, false, "-1 day", true);
        version(policy("legacy", null, "cooperation", true), 1, true, "-1 day", false);
        // An incomplete newer approved version must not silently fall back to old finance.
        version(policy, 2, true, "-1 hour", false);
        authGet(base() + "/agreement-policies", ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        request(version).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_VERSION_INELIGIBLE"));
    }

    @Test
    void signingRequiresActiveOwnerWithManagerRoleAndApprovedCompany() throws Exception {
        long manager = account("manager@example.invalid", "company_manager");
        membership(company, manager, "manager", true);
        authPost(base() + "/agreements/otp", Map.of("policyVersionId", version), session(manager))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AGREEMENT_OWNER_REQUIRED"));
        authPost(base() + "/agreements/otp", Map.of("policyVersionId", version), adminToken).andExpect(status().isNotFound());
        jdbc.update("update trekking.companies set status='pending' where id=?", company);
        request(version).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMPANY_NOT_APPROVED"));
        jdbc.update("update trekking.companies set status='approved' where id=?", company);
        jdbc.update("update trekking.company_members set active=false where company_id=? and account_id=?", company, owner);
        request(version).andExpect(status().isNotFound());
    }

    @Test
    void acceptUsesServerSnapshotsCurrentEmailVerificationAndDirectPeerAddress() throws Exception {
        jdbc.update("update trekking.app_accounts set email_verified_at=current_timestamp-interval '10 days' where id=?", owner);
        request(version).andExpect(status().isAccepted()).andExpect(jsonPath("$.expiresIn").value(300));
        String code = otp();
        var before = OffsetDateTime.now().minusSeconds(1);
        var result = mvc.perform(post(base() + "/agreements").header("Authorization", "Bearer " + access(ownerToken))
                        .header("X-Forwarded-For", "203.0.113.77").with(req -> { req.setRemoteAddr("192.0.2.45"); return req; })
                        .contentType("application/json").content(json.writeValueAsString(Map.of("policyVersionId", version,
                                "otp", code, "acceptedBy", admin, "platformFeePercent", 99, "payoutWaitHours", 0))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedBy").value(owner))
                .andExpect(jsonPath("$.acceptedIp").value("192.0.2.45"))
                .andExpect(jsonPath("$.platformFeePercent").value(7.25)).andExpect(jsonPath("$.payoutWaitHours").value(96))
                .andExpect(jsonPath("$.payoutTermsSnapshot").value("Approved weekly payout terms"))
                .andExpect(jsonPath("$.policyContent").value("Approved cooperation content 1")).andReturn();
        var receipt = json.readTree(result.getResponse().getContentAsString());
        assertThat(OffsetDateTime.parse(receipt.get("emailVerifiedAt").asText())).isAfter(before);
        assertThat(receipt.get("emailVerifiedAt").asText()).isEqualTo(receipt.get("acceptedAt").asText());
        long agreementId = receipt.get("id").asLong();
        authGet(base() + "/agreements/" + agreementId, ownerToken).andExpect(status().isOk());
        authGet(base() + "/agreements", ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        accept(version, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AGREEMENT_ALREADY_ACCEPTED"));
        assertThatThrownBy(() -> jdbc.update("update trekking.company_agreements set payout_wait_hours=0 where id=?", agreementId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from trekking.company_agreements where id=?", agreementId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update trekking.policy_versions set content='changed' where id=?", version))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void otpCannotCrossActorsCompaniesOrVersions() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        long otherOwner = account("other-owner@example.invalid", "company_manager");
        membership(company, otherOwner, "owner", true);
        authPost(base() + "/agreements", Map.of("policyVersionId", version, "otp", code), session(otherOwner))
                .andExpect(status().isBadRequest());
        long anotherCompany = company(admin, "Another Company");
        membership(anotherCompany, owner, "owner", true);
        authPost("/api/v1/companies/" + anotherCompany + "/agreements", Map.of("policyVersionId", version, "otp", code), ownerToken)
                .andExpect(status().isBadRequest());
        long anotherVersion = version(policy("second", null, "cooperation", true), 1, true, "-1 day", true);
        accept(anotherVersion, code).andExpect(status().isBadRequest());
        // Wrong context does not consume the original valid challenge.
        accept(version, code).andExpect(status().isCreated());
    }

    @Test
    void passwordResetCodeCannotAcceptAgreementAndAgreementCodeCannotResetPassword() throws Exception {
        auth.requestOtp("owner@example.invalid", OtpPurpose.RESET_PASSWORD);
        var resetOtp = ArgumentCaptor.forClass(String.class);
        verify(mail).send(org.mockito.ArgumentMatchers.eq("owner@example.invalid"), resetOtp.capture(),
                org.mockito.ArgumentMatchers.eq(OtpPurpose.RESET_PASSWORD));
        accept(version, resetOtp.getValue()).andExpect(status().isBadRequest());
        request(version).andExpect(status().isAccepted());
        String agreementOtp = otp();
        // Remove reset challenge so no accidental numeric equality can affect purpose isolation.
        var resetKeys = redis.keys("auth-test:otp:*");
        if (resetKeys != null && !resetKeys.isEmpty()) redis.delete(resetKeys);
        assertThatThrownBy(() -> auth.resetPassword(new AuthRequests.ResetPassword("owner@example.invalid", agreementOtp, "AnotherStrongPassword123!")))
                .isInstanceOf(com.trekking.app.core.exception.AuthException.class);
        accept(version, agreementOtp).andExpect(status().isCreated());
    }

    @Test
    void failedAttemptsPersistAndFiveFailuresDestroyCode() throws Exception {
        request(version).andExpect(status().isAccepted());
        String correct = otp();
        String wrong = correct.equals("111111") ? "222222" : "111111";
        for (int i = 0; i < 5; i++) accept(version, wrong).andExpect(status().isBadRequest());
        accept(version, correct).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_agreements", Long.class)).isZero();
    }

    @Test
    void expiryAndEmailOrCompanyEvidenceChangesInvalidateChallenge() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        redis.expire(challengeKey(), Duration.ZERO);
        accept(version, code).andExpect(status().isBadRequest());
        clearCooldown();
        request(version).andExpect(status().isAccepted());
        code = otp();
        jdbc.update("update trekking.companies set name='Changed legal company' where id=?", company);
        accept(version, code).andExpect(status().isBadRequest());
        clearCooldown();
        request(version).andExpect(status().isAccepted());
        code = otp();
        jdbc.update("update trekking.app_accounts set email='new-owner@example.invalid' where id=?", owner);
        accept(version, code).andExpect(status().isBadRequest());
    }

    @Test
    void supersededVersionIsRejectedEvenWhenItsCodeIsCorrect() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        long replacement = version(policy, 2, true, "-1 hour", true);
        accept(version, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_VERSION_INELIGIBLE"));
        accept(replacement, code).andExpect(status().isBadRequest());
        authGet(base() + "/agreement-policies", ownerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(replacement));
    }

    @Test
    void revokingMembershipOrManagerRoleAfterOtpPreventsAcceptance() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        jdbc.update("update trekking.company_members set active=false where company_id=? and account_id=?", company, owner);
        accept(version, code).andExpect(status().isNotFound());
        jdbc.update("update trekking.company_members set active=true where company_id=? and account_id=?", company, owner);
        jdbc.update("delete from trekking.account_roles where account_id=? and role='company_manager'", owner);
        accept(version, code).andExpect(status().isNotFound());
    }

    @Test
    void limitsShareEmailAcrossCompanyAndVersionAndCapAtFivePerHour() throws Exception {
        request(version).andExpect(status().isAccepted());
        long second = company(admin, "Second");
        membership(second, owner, "owner", true);
        long secondVersion = version(policy("second", null, "cooperation", true), 1, true, "-1 day", true);
        request(secondVersion).andExpect(status().isTooManyRequests());
        authPost("/api/v1/companies/" + second + "/agreements/otp", Map.of("policyVersionId", version), ownerToken)
                .andExpect(status().isTooManyRequests());
        for (int i = 1; i < 5; i++) {
            clearCooldown();
            request(version).andExpect(status().isAccepted());
        }
        clearCooldown();
        request(version).andExpect(status().isTooManyRequests());
        String storedHash = (String) redis.opsForHash().get(challengeKey(), "hash");
        assertThat(storedHash).hasSize(64).isNotEqualTo(otp());
        assertThat(redis.getExpire(challengeKey())).isBetween(1L, 300L);
    }

    @Test
    void mailFailureInvalidatesCodeAndReturnsRecoverableError() throws Exception {
        doThrow(new MailSendException("Synthetic SMTP failure")).when(agreementMail).send(any(SimpleMailMessage.class));
        request(version).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAIL_UNAVAILABLE"));
        assertThat(redis.hasKey(challengeKey())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_agreements", Long.class)).isZero();
    }

    @Test
    void concurrentAcceptanceCreatesExactlyOneReceipt() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        var start = new CyclicBarrier(2);
        Callable<Integer> operation = () -> { start.await(); return accept(version, code).andReturn().getResponse().getStatus(); };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            assertThat(List.of(first.get(), second.get())).contains(201).allMatch(status -> status == 201 || status == 400 || status == 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_agreements", Long.class)).isEqualTo(1);
    }

    @Test
    void databaseFailureAfterConsumeRequiresANewCode() throws Exception {
        request(version).andExpect(status().isAccepted());
        String code = otp();
        jdbc.execute("""
                create function trekking.fail_agreement_test() returns trigger language plpgsql as $$
                begin raise exception 'Synthetic agreement failure'; end; $$
                """);
        jdbc.execute("create trigger fail_agreement_test before insert on trekking.company_agreements for each row execute function trekking.fail_agreement_test()");
        try {
            accept(version, code).andExpect(status().isServiceUnavailable());
        } finally {
            jdbc.execute("drop trigger fail_agreement_test on trekking.company_agreements");
            jdbc.execute("drop function trekking.fail_agreement_test()");
        }
        accept(version, code).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_agreements", Long.class)).isZero();
        clearCooldown();
        request(version).andExpect(status().isAccepted());
        accept(version, otp()).andExpect(status().isCreated());
    }

    private ResultActions request(long versionId) throws Exception {
        return authPost(base() + "/agreements/otp", Map.of("policyVersionId", versionId), ownerToken);
    }

    private ResultActions accept(long versionId, String code) throws Exception {
        return authPost(base() + "/agreements", Map.of("policyVersionId", versionId, "otp", code), ownerToken);
    }

    private String base() { return "/api/v1/companies/" + company; }
    private String challengeKey() { return "auth-test:agreement:challenge:" + owner + ":" + company; }

    private String otp() {
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(agreementMail, atLeastOnce()).send(capture.capture());
        var message = capture.getValue();
        assertThat(message.getTo()).containsExactly("owner@example.invalid");
        var matcher = Pattern.compile("Ma xac nhan dieu khoan: ([0-9]{6})").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void clearCooldown() {
        var keys = redis.keys("auth-test:agreement:cooldown:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private Map<String, Object> publishBody() {
        return Map.of("content", "Explicit new cooperation terms", "effectiveFrom", OffsetDateTime.now().minusHours(1).toString(),
                "platformFeePercent", 7.25, "payoutWaitHours", 96, "payoutTermsSnapshot", "Approved weekly payout terms");
    }

    private long policy(String code, Long companyId, String kind, boolean active) {
        return jdbc.queryForObject("insert into trekking.policies(code,title,kind,company_id,created_by,active) values (?, ?, ?, ?, ?, ?) returning id",
                Long.class, code, "Policy " + code, kind, companyId, admin, active);
    }

    private long version(long policyId, int number, boolean approved, String effectiveInterval, boolean financialTerms) {
        return jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id,version_number,content,effective_from,approved_by,approved_at,
                    platform_fee_percent,payout_wait_hours,payout_terms_snapshot)
                values (?, ?, ?, current_timestamp + cast(? as interval), ?, ?, ?, ?, ?) returning id
                """, Long.class, policyId, number, "Approved cooperation content " + number, effectiveInterval,
                approved ? admin : null, approved ? OffsetDateTime.now().minusDays(1) : null,
                financialTerms ? new java.math.BigDecimal("7.25") : null, financialTerms ? 96 : null,
                financialTerms ? "Approved weekly payout terms" : null);
    }
}
