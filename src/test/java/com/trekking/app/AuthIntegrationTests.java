package com.trekking.app;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import com.trekking.app.features.auth.service.AuthService;
import com.trekking.app.features.auth.service.OtpMailSender;
import com.trekking.app.features.auth.enums.OtpPurpose;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
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
class AuthIntegrationTests extends AuthTestInfrastructure {
    private static final String EMAIL = "customer@example.invalid";
    private static final String PASSWORD = "SecurePassword123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired
    AuthService service;
    @Autowired RedisAuthStore store;
    @Autowired AuthCrypto crypto;
    @Autowired AuthProperties properties;
    @Autowired JwtEncoder encoder;
    @MockitoBean
    OtpMailSender mail;
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @BeforeEach
    void resetData() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("auth_test");
        jdbc.execute("truncate trekking.app_accounts restart identity cascade");
        var keys = redis.keys("auth-test:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        codes.clear();
        doAnswer(inv -> { codes.put(inv.getArgument(0) + ":" + inv.getArgument(2), inv.getArgument(1)); return null; })
                .when(mail).send(anyString(), anyString(), any());
    }

    @Test
    void registrationVerificationLoginAndMe() throws Exception {
        postJson("/register", Map.of("email", " CUSTOMER@EXAMPLE.INVALID ", "password", PASSWORD,
                "fullName", "Customer", "roles", List.of("admin")))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select role from trekking.account_roles", String.class)).isEqualTo("customer");
        String hash = jdbc.queryForObject("select password_hash from trekking.app_accounts", String.class);
        assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        String code = code(EMAIL, OtpPurpose.VERIFY_EMAIL);
        postJson("/verify-email", Map.of("email", EMAIL, "otp", code)).andExpect(status().isOk());
        postJson("/verify-email", Map.of("email", EMAIL, "otp", code)).andExpect(status().isBadRequest());
        var login = login();
        assertThat(login.getResponse().getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/auth");
        assertThat(login.getResponse().getContentAsString()).doesNotContain("passwordHash", "refreshToken");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(login)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("customer"));
        postJson("/register", Map.of("email", EMAIL.toUpperCase(), "password", PASSWORD, "fullName", "Duplicate"))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongExpiredAndPurposeSeparatedOtp() throws Exception {
        registerVerified(false);
        String code = code(EMAIL, OtpPurpose.VERIFY_EMAIL);
        postJson("/reset-password", Map.of("email", EMAIL, "otp", code, "newPassword", "AnotherPassword123"))
                .andExpect(status().isBadRequest());
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            postJson("/verify-email", Map.of("email", EMAIL, "otp", wrong)).andExpect(status().isBadRequest());
        }
        postJson("/verify-email", Map.of("email", EMAIL, "otp", code)).andExpect(status().isBadRequest());
        redis.delete("auth-test:otp:VERIFY_EMAIL:" + crypto.hmac("email:" + EMAIL) + ":cooldown");
        postJson("/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        redis.expire("auth-test:otp:VERIFY_EMAIL:" + crypto.hmac("email:" + EMAIL), Duration.ZERO);
        postJson("/verify-email", Map.of("email", EMAIL, "otp", code(EMAIL, OtpPurpose.VERIFY_EMAIL)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshReplayRevokesWholeSessionAndLogoutRevokesAccess() throws Exception {
        registerVerified(true);
        var first = login();
        Cookie original = refreshCookie(first);
        var refreshed = mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(original))
                .andExpect(status().isOk()).andReturn();
        assertThat(refreshCookie(refreshed).getValue()).isNotEqualTo(original.getValue());
        mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(refreshed)))
                .andExpect(status().isUnauthorized());
        var next = login();
        mvc.perform(post("/api/v1/auth/logout").with(realCsrf()).cookie(refreshCookie(next)))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(next)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetRevokesAllDevicesAndDoesNotReuseOtp() throws Exception {
        registerVerified(true);
        var first = login();
        var second = login();
        var known = postJson("/forgot-password", Map.of("email", EMAIL)).andExpect(status().isAccepted()).andReturn();
        var unknown = postJson("/forgot-password", Map.of("email", "unknown@example.invalid"))
                .andExpect(status().isAccepted()).andReturn();
        assertThat(known.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        String code = code(EMAIL, OtpPurpose.RESET_PASSWORD);
        postJson("/reset-password", Map.of("email", EMAIL, "otp", code, "newPassword", "NewPassword123!"))
                .andExpect(status().isOk());
        for (var old : List.of(first, second)) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(old)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(refreshCookie(old)))
                    .andExpect(status().isUnauthorized());
        }
        postJson("/reset-password", Map.of("email", EMAIL, "otp", code, "newPassword", PASSWORD)).andExpect(status().isBadRequest());
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isUnauthorized());
        postJson("/login", Map.of("email", EMAIL, "password", "NewPassword123!")).andExpect(status().isOk());
    }

    @Test
    void disabledAndMustResetAccountsCannotLoginAndExistingSessionsStopWorking() throws Exception {
        registerVerified(true);
        var login = login();
        jdbc.update("update trekking.app_accounts set status='disabled'");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access(login))).andExpect(status().isUnauthorized());
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isForbidden());
        jdbc.update("update trekking.app_accounts set status='active', must_change_password=true");
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_RESET_REQUIRED"));
    }

    @Test
    void csrfHandshakeIsDocumentedWithoutClientSuppliedToken() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/csrf'].get.parameters").doesNotExist());
    }

    @Test
    void csrfHandshakeAndCors() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_CSRF"));
        var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/v1/auth/forgot-password").cookie(csrf.getResponse().getCookie("XSRF-TOKEN"))
                        .header("X-XSRF-TOKEN", token).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", EMAIL))))
                .andExpect(status().isAccepted());
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void invalidExpiredWrongIssuerAndWrongAudienceJwtAreRejected() throws Exception {
        registerVerified(true);
        var login = login();
        String sid = store.sessionForRefresh(crypto.digest(refreshCookie(login).getValue())).id();
        String[] parts = access(login).split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("a") ? "b" : "a") + parts[2].substring(1);
        for (String token : List.of("not-a-jwt", forged, signed(sid, "wrong", properties.audience(), Instant.now().plusSeconds(900)),
                signed(sid, properties.issuer(), "wrong", Instant.now().plusSeconds(900)),
                signed(sid, properties.issuer(), properties.audience(), Instant.now().minusSeconds(120)))) {
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void emailAndLoginLimits() throws Exception {
        registerVerified(false);
        postJson("/resend-verification", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        verify(mail, times(1)).send(eq(EMAIL), anyString(), eq(OtpPurpose.VERIFY_EMAIL));
        for (int i = 0; i < 10; i++) {
            postJson("/login", Map.of("email", EMAIL, "password", "incorrect")).andExpect(status().isUnauthorized());
        }
        postJson("/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isTooManyRequests());
    }

    @Test
    void smtpFailureKeepsUnverifiedAccountButInvalidatesCode() throws Exception {
        doThrow(new MailSendException("test SMTP failure")).when(mail).send(anyString(), anyString(), any());
        postJson("/register", Map.of("email", EMAIL, "password", PASSWORD, "fullName", "Customer"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MAIL_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("select count(*) from trekking.app_accounts where email_verified_at is null", Integer.class)).isEqualTo(1);
        assertThat(redis.hasKey("auth-test:otp:VERIFY_EMAIL:" + crypto.hmac("email:" + EMAIL))).isFalse();
        postJson("/forgot-password", Map.of("email", EMAIL)).andExpect(status().isAccepted());
    }

    @Test
    void otpAndRefreshAreAtomicUnderConcurrentUse() throws Exception {
        registerVerified(false);
        var request = new AuthRequests.VerifyEmail(EMAIL, code(EMAIL, OtpPurpose.VERIFY_EMAIL));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var outcomes = pool.<Integer>invokeAll(List.of(
                    () -> attempt(() -> service.verifyEmail(request)),
                    () -> attempt(() -> service.verifyEmail(request))));
            assertThat(outcomes.get(0).get() + outcomes.get(1).get()).isEqualTo(1);
            var tokens = service.login(new AuthRequests.Login(EMAIL, PASSWORD), "127.0.0.1");
            var refreshes = pool.<Integer>invokeAll(List.of(
                    () -> attempt(() -> service.refresh(tokens.refreshToken())),
                    () -> attempt(() -> service.refresh(tokens.refreshToken()))));
            assertThat(refreshes.get(0).get() + refreshes.get(1).get()).isEqualTo(1);
            assertThatThrownBy(() -> service.refresh(tokens.refreshToken())).isInstanceOf(AuthException.class);
        }
    }

    @Test
    void duplicateConcurrentRegistrationCreatesOnlyOneAccountAndRole() throws Exception {
        var request = new AuthRequests.Register(EMAIL, PASSWORD, "Customer", null);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var outcomes = pool.<Integer>invokeAll(List.of(() -> attempt(() -> service.register(request)),
                    () -> attempt(() -> service.register(request))));
            assertThat(outcomes.get(0).get() + outcomes.get(1).get()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from trekking.app_accounts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from trekking.account_roles", Integer.class)).isEqualTo(1);
        verify(mail, times(1)).send(eq(EMAIL), anyString(), eq(OtpPurpose.VERIFY_EMAIL));
    }

    @Test
    void expiredRefreshIsRejectedAndHourlyMailLimitPersistsAfterCooldown() throws Exception {
        registerVerified(true);
        var login = login();
        String hash = crypto.digest(refreshCookie(login).getValue());
        redis.expire("auth-test:refresh:" + hash, Duration.ZERO);
        mvc.perform(post("/api/v1/auth/refresh").with(realCsrf()).cookie(refreshCookie(login)))
                .andExpect(status().isUnauthorized());
        String email = "limits@example.invalid";
        String cooldown = "auth-test:otp:RESET_PASSWORD:" + crypto.hmac("email:" + email) + ":cooldown";
        for (int i = 0; i < 5; i++) {
            assertThat(store.reserveEmail(email, OtpPurpose.RESET_PASSWORD)).isTrue();
            redis.delete(cooldown);
        }
        assertThat(store.reserveEmail(email, OtpPurpose.RESET_PASSWORD)).isFalse();
    }

    @Test
    void resendingReplacesOldOtpAndRawSecretsAreNotStored() throws Exception {
        registerVerified(false);
        String key = "auth-test:otp:VERIFY_EMAIL:" + crypto.hmac("email:" + EMAIL);
        String old = code(EMAIL, OtpPurpose.VERIFY_EMAIL);
        assertThat(redis.opsForHash().get(key, "hash")).isNotEqualTo(old);
        assertThat(redis.getExpire(key)).isBetween(1L, 300L);
        String newHash = crypto.hmac("replacement");
        String context = (String) redis.opsForHash().get(key, "context");
        store.putOtp(EMAIL, OtpPurpose.VERIFY_EMAIL, newHash, context);
        postJson("/verify-email", Map.of("email", EMAIL, "otp", old)).andExpect(status().isBadRequest());
        store.consumeOtp(EMAIL, OtpPurpose.VERIFY_EMAIL, newHash, context);
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void legacyMixedCaseEmailCanResetUsingNormalizedEmail() throws Exception {
        registerVerified(true);
        jdbc.update("update trekking.app_accounts set email = ?", " CUSTOMER@EXAMPLE.INVALID ");
        postJson("/forgot-password", Map.of("email", EMAIL)).andExpect(status().isAccepted());
        postJson("/reset-password", Map.of("email", EMAIL, "otp", code(EMAIL, OtpPurpose.RESET_PASSWORD),
                "newPassword", "NewPassword123!")).andExpect(status().isOk());
        postJson("/login", Map.of("email", EMAIL, "password", "NewPassword123!")).andExpect(status().isOk());
    }

    private int attempt(Runnable action) {
        try { action.run(); return 1; } catch (AuthException ex) { return 0; }
    }

    private String signed(String sid, String issuer, String audience, Instant expires) {
        var claims = JwtClaimsSet.builder().subject("1").issuer(issuer).audience(List.of(audience))
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(expires).claim("sid", sid).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private void registerVerified(boolean verify) throws Exception {
        postJson("/register", Map.of("email", EMAIL, "password", PASSWORD, "fullName", "Customer"))
                .andExpect(status().isCreated());
        if (verify) postJson("/verify-email", Map.of("email", EMAIL, "otp", code(EMAIL, OtpPurpose.VERIFY_EMAIL)))
                .andExpect(status().isOk());
    }

    private String code(String email, OtpPurpose purpose) {
        String code = codes.get(email + ":" + purpose);
        assertThat(code).matches("[0-9]{6}");
        return code;
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post("/api/v1/auth" + path).with(realCsrf()).contentType("application/json").content(json.writeValueAsString(body)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return request -> {
            Cookie[] existing = request.getCookies();
            var cookies = new java.util.ArrayList<Cookie>();
            if (existing != null) cookies.addAll(List.of(existing));
            cookies.add(cookie);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader(body.get("headerName").asText(), body.get("token").asText());
            return request;
        };
    }

    private MvcResult login() throws Exception {
        return postJson("/login", Map.of("email", EMAIL, "password", PASSWORD)).andExpect(status().isOk()).andReturn();
    }

    private String access(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Cookie refreshCookie(MvcResult result) { return result.getResponse().getCookie("trekking_refresh"); }
}
