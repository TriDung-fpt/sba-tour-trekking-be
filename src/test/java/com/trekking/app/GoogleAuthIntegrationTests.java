package com.trekking.app;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.account.dto.request.AuthRequests;
import com.trekking.app.features.account.enums.OtpPurpose;
import com.trekking.app.features.account.service.auth.AuthService;
import com.trekking.app.features.account.service.google.GoogleIdentity;
import com.trekking.app.features.account.service.google.GoogleTokenVerifier;
import com.trekking.app.features.account.service.otp.OtpMailSender;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GoogleAuthIntegrationTests extends AuthTestInfrastructure {
    private static final String EMAIL = "customer@gmail.com";
    private static final String PASSWORD = "SecurePassword123!";
    private static final String ID_TOKEN = "verified-google-id-token";
    private static final String SUBJECT = "google-subject-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired AuthService service;
    @MockitoBean GoogleTokenVerifier google;
    @MockitoBean OtpMailSender mail;
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void resetData() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("auth_test");
        jdbc.execute("truncate trekking.app_accounts restart identity cascade");
        var keys = redis.keys("auth-test:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        codes.clear();
        when(google.verify(ID_TOKEN)).thenReturn(identity(SUBJECT, EMAIL, true));
        doAnswer(invocation -> {
            codes.put(invocation.getArgument(0) + ":" + invocation.getArgument(2), invocation.getArgument(1));
            return null;
        }).when(mail).send(anyString(), anyString(), any());
    }

    @Test
    void createsVerifiedCustomerAndReturningSubjectKeepsLocalIdentityWhenGoogleEmailChanges() throws Exception {
        var first = postJson("/google", Map.of("idToken", ID_TOKEN, "email", "attacker@example.invalid",
                "roles", List.of("admin"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.email").value(EMAIL))
                .andExpect(jsonPath("$.account.roles[0]").value("customer"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        long accountId = json.readTree(first.getResponse().getContentAsString()).get("account").get("id").asLong();
        String passwordHash = passwordHash();
        assertThat(passwordHash).startsWith("$2").doesNotContain(ID_TOKEN, SUBJECT);
        assertThat(jdbc.queryForObject("select email_verified_at is not null from trekking.app_accounts", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select google_subject from trekking.app_accounts", String.class)).isEqualTo(SUBJECT);
        assertThat(first.getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
        assertThat(first.getResponse().getContentAsString()).doesNotContain("passwordHash", "refreshToken", "googleSubject");
        assertMe(first, EMAIL);

        when(google.verify(ID_TOKEN)).thenReturn(new GoogleIdentity(SUBJECT, "changed@external.example", "Changed Name", false));
        var returning = googleLogin().andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(accountId))
                .andExpect(jsonPath("$.account.email").value(EMAIL))
                .andExpect(jsonPath("$.account.fullName").value("Google Customer"))
                .andExpect(jsonPath("$.account.roles[0]").value("customer")).andReturn();
        assertMe(returning, EMAIL);
        assertThat(passwordHash()).isEqualTo(passwordHash);
        assertCounts(1, 1);
        verifyNoInteractions(mail);
    }

    @Test
    void linksVerifiedLocalAccountWithoutReplacingPasswordProfileOrRoles() throws Exception {
        registerLocal(true);
        jdbc.update("insert into trekking.account_roles(account_id, role) values (1, 'support_staff')");
        String originalPassword = passwordHash();
        long originalId = jdbc.queryForObject("select id from trekking.app_accounts", Long.class);
        googleLogin().andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(originalId))
                .andExpect(jsonPath("$.account.fullName").value("Local Customer"))
                .andExpect(jsonPath("$.account.phone").value("0901234567"))
                .andExpect(jsonPath("$.account.roles[0]").value("customer"))
                .andExpect(jsonPath("$.account.roles[1]").value("support_staff"));
        assertThat(passwordHash()).isEqualTo(originalPassword);
        assertThat(jdbc.queryForObject("select google_subject from trekking.app_accounts", String.class)).isEqualTo(SUBJECT);
        assertCounts(1, 2);
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isOk());
        verify(mail, times(1)).send(eq(EMAIL), anyString(), eq(OtpPurpose.VERIFY_EMAIL));
    }

    @Test
    void doesNotLinkOrVerifyUnverifiedLocalAccount() throws Exception {
        registerLocal(false);
        googleLogin().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        assertThat(jdbc.queryForObject("select google_subject is null and email_verified_at is null from trekking.app_accounts", Boolean.class)).isTrue();
        assertNoSessions();
        assertCounts(1, 1);
    }

    @Test
    void localAccountRestrictionsCannotBeBypassedByGoogleLinking() throws Exception {
        registerLocal(true);
        for (String state : List.of("disabled", "blocked")) {
            jdbc.update("update trekking.app_accounts set status=?, blocked_reason='Test account restriction'", state);
            googleLogin().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_UNAVAILABLE"));
        }
        jdbc.update("update trekking.app_accounts set status='active', must_change_password=true");
        googleLogin().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));
        assertThat(jdbc.queryForObject("select google_subject is null from trekking.app_accounts", Boolean.class)).isTrue();
        assertNoSessions();
    }

    @Test
    void restrictionsAlsoApplyToAlreadyLinkedGoogleAccountAndItsSessions() throws Exception {
        var login = googleLogin().andExpect(status().isOk()).andReturn();
        jdbc.update("update trekking.app_accounts set status='disabled'");
        googleLogin().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_UNAVAILABLE"));
        assertSessionRejected(login);
        jdbc.update("update trekking.app_accounts set status='active', must_change_password=true");
        googleLogin().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));
        assertCounts(1, 1);
    }

    @Test
    void thirdPartyEmailCannotCreateOrLinkAccountWithoutFreshOwnershipVerification() throws Exception {
        when(google.verify(ID_TOKEN)).thenReturn(identity(SUBJECT, "customer@external.example", false));
        googleLogin().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_VERIFICATION_REQUIRED"));
        assertCounts(0, 0);
        registerLocal(true);
        when(google.verify(ID_TOKEN)).thenReturn(identity(SUBJECT, EMAIL, false));
        googleLogin().andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_VERIFICATION_REQUIRED"));
        assertThat(jdbc.queryForObject("select google_subject is null from trekking.app_accounts", Boolean.class)).isTrue();
        assertNoSessions();
    }

    @Test
    void differentGoogleSubjectCannotTakeOverAlreadyLinkedEmail() throws Exception {
        googleLogin().andExpect(status().isOk());
        when(google.verify(ID_TOKEN)).thenReturn(identity("different-google-subject", EMAIL, true));
        googleLogin().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_CONFLICT"));
        assertThat(jdbc.queryForObject("select google_subject from trekking.app_accounts", String.class)).isEqualTo(SUBJECT);
        assertCounts(1, 1);
        assertThat(redis.keys("auth-test:session:*")).hasSize(1);
    }

    @Test
    void concurrentFirstLoginCreatesOneAccountAndRoleAndBothRequestsSucceed() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return service.loginGoogle(new AuthRequests.GoogleLogin(ID_TOKEN), "127.0.0.1");
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var first = calls.get(0).get(30, TimeUnit.SECONDS);
            var second = calls.get(1).get(30, TimeUnit.SECONDS);
            assertThat(first.response().account().id()).isEqualTo(second.response().account().id());
            assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
            assertThat(service.refresh(first.refreshToken()).response().account().email()).isEqualTo(EMAIL);
            assertThat(service.refresh(second.refreshToken()).response().account().email()).isEqualTo(EMAIL);
        } finally {
            start.countDown();
        }
        assertCounts(1, 1);
        assertThat(redis.keys("auth-test:session:*")).hasSize(2);
        verifyNoInteractions(mail);
    }

    @Test
    void googleSessionsUseRefreshReplayProtectionLogoutAndPasswordResetRevocation() throws Exception {
        var first = googleLogin().andExpect(status().isOk()).andReturn();
        var refreshed = mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(refreshCookie(first)))
                .andExpect(status().isOk()).andReturn();
        assertThat(refreshCookie(refreshed).getValue()).isNotEqualTo(refreshCookie(first).getValue());
        mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(refreshCookie(first))).andExpect(status().isUnauthorized());
        assertSessionRejected(refreshed);

        var logout = googleLogin().andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/logout").with(realCsrf()).cookie(refreshCookie(logout)))
                .andExpect(status().isNoContent());
        assertSessionRejected(logout);

        var deviceOne = googleLogin().andExpect(status().isOk()).andReturn();
        var deviceTwo = googleLogin().andExpect(status().isOk()).andReturn();
        postJson("/forgot-password", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        postJson("/reset-password", Map.of("email", EMAIL, "otp", code(OtpPurpose.RESET_PASSWORD),
                "newPassword", "NewPassword123!")).andExpect(status().isOk());
        assertSessionRejected(deviceOne);
        assertSessionRejected(deviceTwo);
        postJson("/login", Map.of("email", EMAIL, "password", "NewPassword123!")).andExpect(status().isOk());
        googleLogin().andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select google_subject from trekking.app_accounts", String.class)).isEqualTo(SUBJECT);
    }

    @Test
    void requiresCsrfAndRejectsBlankMissingAndOversizedTokenBeforeVerification() throws Exception {
        mvc.perform(post("/api/v1/auth/google").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("idToken", ID_TOKEN))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF"));
        for (Object body : List.of(Map.of("idToken", " "), Map.of(), Map.of("idToken", "x".repeat(20_000)))) {
            postJson("/google", body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(google);
        assertCounts(0, 0);
        assertNoSessions();
    }

    @Test
    void tokenVerificationFailureNeverCreatesAccountOrSession() throws Exception {
        doThrow(new AuthException(HttpStatus.UNAUTHORIZED,
                "INVALID_GOOGLE_TOKEN", "Google token is invalid")).when(google).verify(ID_TOKEN);
        googleLogin().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));
        doThrow(new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "GOOGLE_AUTH_UNAVAILABLE", "Google verification is unavailable")).when(google).verify(ID_TOKEN);
        googleLogin().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_AUTH_UNAVAILABLE"));
        assertCounts(0, 0);
        assertNoSessions();
        verifyNoInteractions(mail);
    }

    private GoogleIdentity identity(String subject, String email, boolean authoritative) {
        return new GoogleIdentity(subject, email, "Google Customer", authoritative);
    }

    private void registerLocal(boolean verified) throws Exception {
        postJson("/register", Map.of("email", EMAIL, "password", PASSWORD,
                "fullName", "Local Customer", "phone", "0901234567")).andExpect(status().isCreated());
        if (verified) postJson("/verify-email", Map.of("email", EMAIL, "otp", code(OtpPurpose.VERIFY_EMAIL)))
                .andExpect(status().isOk());
    }

    private String code(OtpPurpose purpose) {
        String code = codes.get(EMAIL + ":" + purpose);
        assertThat(code).matches("[0-9]{6}");
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions googleLogin() throws Exception {
        return postJson("/google", Map.of("idToken", ID_TOKEN));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post("/api/v1/auth" + path).with(realCsrf())
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return request -> {
            var cookies = new java.util.ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(List.of(request.getCookies()));
            cookies.add(cookie);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader(body.get("headerName").asText(), body.get("token").asText());
            return request;
        };
    }

    private void assertMe(MvcResult login, String email) throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(login)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    private void assertSessionRejected(MvcResult login) throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(login)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(refreshCookie(login)))
                .andExpect(status().isUnauthorized());
    }

    private void assertCounts(int accounts, int roles) {
        assertThat(jdbc.queryForObject("select count(*) from trekking.app_accounts", Integer.class)).isEqualTo(accounts);
        assertThat(jdbc.queryForObject("select count(*) from trekking.account_roles", Integer.class)).isEqualTo(roles);
    }

    private void assertNoSessions() { assertThat(redis.keys("auth-test:session:*")).isEmpty(); }

    private String passwordHash() { return jdbc.queryForObject("select password_hash from trekking.app_accounts", String.class); }

    private String access(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("trekking_refresh");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
