package com.trekking.app;

import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.auth.enums.OtpPurpose;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import com.trekking.app.features.invitation.service.InvitationMailSender;
import com.trekking.app.features.invitation.repository.RedisInvitationStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InvitationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String STAFF = "/api/v1/admin/staff/invitations";
    private static final String ACTIVATE = "/api/v1/auth/activate-account";
    private static final String EMAIL = "invited@example.invalid";
    private static final String INITIAL_PASSWORD = "InitialPassword123!";
    @MockitoBean InvitationMailSender invitationsMail;
    @MockitoSpyBean RedisInvitationStore invitationStore;
    @Autowired AuthCrypto crypto;
    @Autowired RedisAuthStore authStore;

    @ParameterizedTest
    @ValueSource(strings = {"support_staff", "content_staff", "accountant"})
    void adminInvitesStaffAndActivationVerifiesEmailSetsPasswordWithoutIssuingSession(String role) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        doAnswer(call -> {
            // Mail is invoked only after account, role and audit are externally visible in PostgreSQL.
            assertThat(jdbc.queryForObject("select count(*) from trekking.app_accounts where email = ?", Integer.class, EMAIL)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs where action = 'INVITATION_CREATED'", Integer.class)).isEqualTo(1);
            return null;
        }).when(invitationsMail).send(eq(EMAIL), anyString());
        var created = authPost(STAFF, staff(role), session(admin)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.activationRequired").value(true))
                .andExpect(jsonPath("$.deliveryStatus").value("sent"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("accountId").asLong();
        assertThat(jdbc.queryForObject("select must_change_password from trekking.app_accounts where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select email_verified_at from trekking.app_accounts where id = ?", Object.class, id)).isNull();
        assertThat(passwords.matches(INITIAL_PASSWORD, passwordHash(id))).isFalse();
        String code = code();
        assertThat(redis.opsForHash().entries(challenge())).doesNotContainValue(code).doesNotContainValue(INITIAL_PASSWORD);
        activate(code).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(cookie().doesNotExist("trekking_refresh"));
        assertThat(passwords.matches(INITIAL_PASSWORD, passwordHash(id))).isTrue();
        assertThat(jdbc.queryForObject("select must_change_password from trekking.app_accounts where id = ?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("select email_verified_at is not null from trekking.app_accounts where id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select details::text from trekking.audit_logs where action = 'INVITATION_ACTIVATED'", String.class))
                .doesNotContain(code, INITIAL_PASSWORD, "passwordHash");
        activate(code).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").with(realCsrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", INITIAL_PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.roles[0]").value(role));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "company_manager", "guide", "customer", "SUPPORT_STAFF"})
    void staffRoleWhitelistRejectsEscalation(String role) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        authPost(STAFF, staff(role), session(admin)).andExpect(status().isBadRequest());
        assertThat(countEmail()).isZero();
        verifyNoInteractions(invitationsMail);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "company_manager", "guide", "support_staff", "content_staff", "accountant"})
    void nonAdminsCannotInviteStaff(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        authPost(STAFF, staff("accountant"), session(caller)).andExpect(status().isForbidden());
        assertThat(countEmail()).isZero();
        verifyNoInteractions(invitationsMail);
    }

    @Test
    void guideRequiresApprovedCompanyAndActiveScopedManagerAndIgnoresInjectedRole() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager");
        long companyA = company(admin, "Company A");
        long companyB = company(admin, "Company B");
        membership(companyA, manager, "owner", true);
        var token = session(manager);
        var request = Map.of("email", EMAIL, "fullName", "Invited Guide", "role", "admin", "companyId", companyB);
        authPost(guidePath(companyB), request, token).andExpect(status().isNotFound());
        authPost(guidePath(companyA), request, session(admin)).andExpect(status().isNotFound());
        jdbc.update("update trekking.companies set status = 'suspended' where id = ?", companyA);
        authPost(guidePath(companyA), request, token).andExpect(status().isNotFound());
        jdbc.update("update trekking.companies set status = 'approved' where id = ?", companyA);
        authPost(guidePath(companyA), request, token).andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("guide"))
                .andExpect(jsonPath("$.companyId").value(companyA));
        long invited = id();
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, invited)).containsExactly("guide");
        assertThat(jdbc.queryForObject("select company_id from trekking.guides where account_id = ?", Long.class, invited)).isEqualTo(companyA);
        jdbc.update("update trekking.company_members set active = false where company_id = ? and account_id = ?", companyA, manager);
        clearCooldown();
        authPost(guidePath(companyA) + "/" + invited + "/resend", Map.of(), token).andExpect(status().isNotFound());
    }

    @Test
    void existingEmailNeverChangesPasswordOrRoles() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long existing = account(EMAIL, "customer");
        String hash = passwordHash(existing);
        authPost(STAFF, staff("accountant"), session(admin)).andExpect(status().isConflict());
        assertThat(passwordHash(existing)).isEqualTo(hash);
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, existing)).containsExactly("customer");
        verifyNoInteractions(invitationsMail);
    }

    @Test
    void redisDeliveryFailureReturnsRecoverableCommittedInvitation() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        doThrow(new DataAccessResourceFailureException("simulated challenge outage"))
                .when(invitationStore).issue(anyString(), anyString(), anyString());
        var result = authPost(STAFF, staff("support_staff"), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryStatus").value("retry_required"))
                .andExpect(jsonPath("$.accountId").isNumber()).andReturn();
        long accountId = json.readTree(result.getResponse().getContentAsString()).get("accountId").asLong();
        assertThat(countEmail()).isEqualTo(1);
        verifyNoInteractions(invitationsMail);
        doCallRealMethod().when(invitationStore).issue(anyString(), anyString(), anyString());
        authPost(STAFF + "/" + accountId + "/resend", Map.of(), token).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryStatus").value("sent"));
        activate(code()).andExpect(status().isOk());
    }

    @Test
    void smtpFailureLeavesPendingAccountAndAllowsBoundedResendWithoutChangingCredential() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        doThrow(new MailSendException("simulated delivery failure")).when(invitationsMail).send(anyString(), anyString());
        authPost(STAFF, staff("support_staff"), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryStatus").value("retry_required"));
        long invited = id();
        String hash = passwordHash(invited);
        assertThat(redis.hasKey(challenge())).isFalse();
        authPost(STAFF + "/" + invited + "/resend", Map.of(), token).andExpect(status().isTooManyRequests());
        clearCooldown();
        doNothing().when(invitationsMail).send(anyString(), anyString());
        authPost(STAFF + "/" + invited + "/resend", Map.of(), token).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deliveryStatus").value("sent"));
        assertThat(passwordHash(invited)).isEqualTo(hash);
        activate(code()).andExpect(status().isOk());
        clearCooldown();
        authPost(STAFF + "/" + invited + "/resend", Map.of(), token).andExpect(status().isConflict());
        assertThat(passwords.matches(INITIAL_PASSWORD, passwordHash(invited))).isTrue();
    }

    @Test
    void resendReplacesOldCodeAndEnforcesFivePerHour() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        authPost(STAFF, staff("support_staff"), token).andExpect(status().isCreated());
        long invited = id();
        String oldHash = "obsolete-challenge-hash";
        redis.opsForHash().put(challenge(), "hash", oldHash);
        for (int i = 0; i < 4; i++) {
            clearCooldown();
            authPost(STAFF + "/" + invited + "/resend", Map.of(), token).andExpect(status().isAccepted());
        }
        assertThat(redis.opsForHash().get(challenge(), "hash")).isNotEqualTo(oldHash);
        clearCooldown();
        authPost(STAFF + "/" + invited + "/resend", Map.of(), token).andExpect(status().isTooManyRequests());
        verify(invitationsMail, times(5)).send(eq(EMAIL), anyString());
        activate(code()).andExpect(status().isOk());
    }

    @Test
    void fiveWrongCodesExhaustChallengeAndExpirationIsEnforced() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        authPost(STAFF, staff("support_staff"), token).andExpect(status().isCreated());
        String code = code();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) activate(wrong).andExpect(status().isBadRequest());
        activate(code).andExpect(status().isBadRequest());
        assertThat(redis.hasKey(challenge())).isFalse();
        clearCooldown();
        authPost(STAFF + "/" + id() + "/resend", Map.of(), token).andExpect(status().isAccepted());
        assertThat(redis.getExpire(challenge())).isBetween(295L, 300L);
        redis.expire(challenge(), Duration.ZERO);
        activate(code()).andExpect(status().isBadRequest());
    }

    @Test
    void activationRequiresCsrfAndValidPassword() throws Exception {
        invite();
        mvc.perform(post(ACTIVATE).contentType("application/json").content(json.writeValueAsString(activation(code()))))
                .andExpect(status().isForbidden());
        mvc.perform(post(ACTIVATE).with(realCsrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "otp", code(), "newPassword", "short"))))
                .andExpect(status().isBadRequest());
        activate(code()).andExpect(status().isOk());
    }

    @Test
    void pendingActivationCannotUseExistingVerifyOrResetChallenges() throws Exception {
        invite();
        long invited = id();
        String hash = passwordHash(invited);
        String context = invited + ":" + crypto.passwordFingerprint(hash);
        for (OtpPurpose purpose : OtpPurpose.values()) {
            authStore.putOtp(EMAIL, purpose, crypto.hmac("otp:" + purpose + ":" + EMAIL + ":123456"), context);
        }
        mvc.perform(post("/api/v1/auth/verify-email").with(realCsrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "otp", "123456")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/reset-password").with(realCsrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "otp", "123456", "newPassword", INITIAL_PASSWORD))))
                .andExpect(status().isBadRequest());
        auth.requestOtp(EMAIL, OtpPurpose.VERIFY_EMAIL);
        auth.requestOtp(EMAIL, OtpPurpose.RESET_PASSWORD);
        verifyNoInteractions(mail);
        assertThat(passwordHash(invited)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("select must_change_password from trekking.app_accounts where id = ?", Boolean.class, invited)).isTrue();
        activate(code()).andExpect(status().isOk());
    }

    @Test
    void accountStateAndIdentityChangesInvalidateActivation() throws Exception {
        invite();
        long invited = id();
        String code = code();
        jdbc.update("update trekking.app_accounts set status = 'blocked', blocked_reason = 'test' where id = ?", invited);
        activate(code).andExpect(status().isBadRequest());
        jdbc.update("update trekking.app_accounts set status = 'active', blocked_reason = null, session_version = session_version + 1 where id = ?", invited);
        activate(code).andExpect(status().isBadRequest());
        assertThat(redis.hasKey(challenge())).isFalse();
    }

    @Test
    void guideActivationRejectsDisabledGuideAndSuspendedCompany() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager");
        long company = company(admin, "Approved Company");
        membership(company, manager, "manager", true);
        authPost(guidePath(company), Map.of("email", EMAIL, "fullName", "Guide"), session(manager)).andExpect(status().isCreated());
        String code = code();
        jdbc.update("update trekking.guides set active = false where account_id = ?", id());
        activate(code).andExpect(status().isBadRequest());
        jdbc.update("update trekking.guides set active = true where account_id = ?", id());
        jdbc.update("update trekking.companies set status = 'suspended' where id = ?", company);
        activate(code).andExpect(status().isBadRequest());
    }

    @Test
    void auditFailureRollsBackAccountRoleAndGuideAndDoesNotSendMail() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager");
        long company = company(admin, "Approved Company");
        membership(company, manager, "owner", true);
        var token = session(manager);
        jdbc.execute("alter table trekking.audit_logs add constraint qa_invitation_audit check (action <> 'INVITATION_CREATED')");
        try {
            authPost(guidePath(company), Map.of("email", EMAIL, "fullName", "Guide"), token).andExpect(status().isServiceUnavailable());
            assertThat(countEmail()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from trekking.guides", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from trekking.account_roles where role = 'guide'", Integer.class)).isZero();
            verifyNoInteractions(invitationsMail);
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_invitation_audit");
        }
    }

    @Test
    void activationAuditFailureRollsBackVerificationAndPasswordAndRequiresNewCode() throws Exception {
        invite();
        long invited = id();
        String before = passwordHash(invited);
        String code = code();
        jdbc.execute("alter table trekking.audit_logs add constraint qa_activation_audit check (action <> 'INVITATION_ACTIVATED')");
        try {
            activate(code).andExpect(status().isServiceUnavailable());
            assertThat(passwordHash(invited)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select must_change_password from trekking.app_accounts where id = ?", Boolean.class, invited)).isTrue();
            assertThat(jdbc.queryForObject("select email_verified_at from trekking.app_accounts where id = ?", Object.class, invited)).isNull();
            activate(code).andExpect(status().isBadRequest());
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_activation_audit");
        }
    }

    @Test
    void concurrentCreationHasOneWinnerAndConcurrentActivationConsumesOnlyOnce() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> create = () -> authPost(STAFF, staff("support_staff"), token).andReturn().getResponse().getStatus();
            var created = executor.invokeAll(List.of(create, create));
            assertThat(List.of(created.get(0).get(), created.get(1).get())).containsExactlyInAnyOrder(201, 409);
            assertThat(countEmail()).isEqualTo(1);
            String code = code();
            // Prepare two independent valid CSRF requests before starting the race.
            var firstCsrf = realCsrf();
            var secondCsrf = realCsrf();
            String body = json.writeValueAsString(activation(code));
            Callable<Integer> first = () -> mvc.perform(post(ACTIVATE).with(firstCsrf).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            Callable<Integer> second = () -> mvc.perform(post(ACTIVATE).with(secondCsrf).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            var activated = executor.invokeAll(List.of(first, second));
            assertThat(List.of(activated.get(0).get(), activated.get(1).get())).containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs where action = 'INVITATION_ACTIVATED'", Integer.class)).isEqualTo(1);
        verify(invitationsMail).send(eq(EMAIL), anyString());
    }

    private void invite() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        authPost(STAFF, staff("support_staff"), session(admin)).andExpect(status().isCreated());
    }

    private Map<String, String> staff(String role) { return Map.of("email", EMAIL, "fullName", "Invited Staff", "role", role); }
    private Map<String, String> activation(String code) { return Map.of("email", EMAIL, "otp", code, "newPassword", INITIAL_PASSWORD); }
    private ResultActions activate(String code) throws Exception {
        return mvc.perform(post(ACTIVATE).with(realCsrf()).contentType("application/json").content(json.writeValueAsString(activation(code))));
    }
    private String guidePath(long companyId) { return "/api/v1/companies/" + companyId + "/guides/invitations"; }
    private long id() { return jdbc.queryForObject("select id from trekking.app_accounts where email = ?", Long.class, EMAIL); }
    private int countEmail() { return jdbc.queryForObject("select count(*) from trekking.app_accounts where email = ?", Integer.class, EMAIL); }
    private String passwordHash(long accountId) { return jdbc.queryForObject("select password_hash from trekking.app_accounts where id = ?", String.class, accountId); }
    private String challenge() { return "auth-test:invitation:activate:" + crypto.hmac("email:" + EMAIL); }
    private void clearCooldown() { redis.delete(challenge() + ":cooldown"); }
    private String code() {
        var captor = ArgumentCaptor.forClass(String.class);
        verify(invitationsMail, atLeastOnce()).send(eq(EMAIL), captor.capture());
        return captor.getValue();
    }
}
