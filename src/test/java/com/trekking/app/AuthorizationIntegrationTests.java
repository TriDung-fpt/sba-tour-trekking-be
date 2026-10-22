package com.trekking.app;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.auth.service.AuthService;
import com.trekking.app.features.auth.service.OtpMailSender;
import com.trekking.app.features.auth.model.IssuedTokens;
import com.trekking.app.features.account.enums.RoleType;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthorizationIntegrationTests.ScopeProbeController.class)
class AuthorizationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String AUTHORIZATION = "/api/v1/accounts/me/authorization";
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "ACCOUNT_READ", "ACCOUNT_BLOCK", "ACCOUNT_ROLES_WRITE", "POLICY_MANAGE",
            "FINANCE_READ", "PAYMENT_RECORD", "REFUND_APPROVE", "REFUND_RECORD",
            "TOUR_READ", "TOUR_MANAGE", "TOUR_COMPOSE", "TOUR_PUBLISH", "SCHEDULE_MANAGE",
            "BOOKING_MANAGE", "GUIDE_MANAGE");

    @Test
    void authorizationRequiresCurrentAuthenticatedSession() throws Exception {
        mvc.perform(get(AUTHORIZATION)).andExpect(status().isUnauthorized());
        mvc.perform(get(AUTHORIZATION).header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authPublicAllowlistDoesNotExposeUnknownPathsOrUnsupportedMethods() throws Exception {
        mvc.perform(get("/api/v1/auth/future-sensitive-action")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/activate-account")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(RoleType.class)
    void threeRoleCapabilitiesContainNoCompanyContextOrLegacyPermissions(RoleType role) throws Exception {
        long caller = account("caller@example.invalid", role.getValue());
        var result = authGet(AUTHORIZATION, session(caller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(caller))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value(role.getValue()))
                .andExpect(jsonPath("$.companies").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        var permissions = new ArrayList<String>();
        json.readTree(result.getResponse().getContentAsString()).get("permissions")
                .forEach(permission -> permissions.add(permission.asText()));
        if (role == RoleType.ADMIN) {
            assertThat(permissions).containsExactlyElementsOf(ADMIN_PERMISSIONS.stream().sorted().toList());
        } else {
            assertThat(permissions).isEmpty();
        }
    }

    @Test
    void accountWithoutRolesHasNoCapabilities() throws Exception {
        long caller = account("no-role@example.invalid");
        authGet(AUTHORIZATION, session(caller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.companies").doesNotExist());
    }

    @Test
    void combinedRolesAndRevocationAreVisibleWithoutIssuingANewAccessToken() throws Exception {
        long caller = account("caller@example.invalid", "admin", "customer", "guide");
        var token = session(caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("admin", "customer", "guide")))
                .andExpect(jsonPath("$.permissions.length()").value(ADMIN_PERMISSIONS.size()));
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'admin'", caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("customer", "guide")))
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void accountStatusRevocationStopsAuthorizationResponseForExistingSession() throws Exception {
        long caller = account("caller@example.invalid", "guide");
        var token = session(caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk());
        jdbc.update("update trekking.app_accounts set status = 'disabled' where id = ?", caller);
        authGet(AUTHORIZATION, token).andExpect(status().isUnauthorized());
    }

    @Test
    void guideNeedsCurrentRoleAnActiveProfileAndExactAssignment() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long caller = account("guide@example.invalid", "guide", "customer");
        long other = account("other@example.invalid", "guide");
        long profile = internalGuide(caller);
        long otherProfile = internalGuide(other);
        long assigned = internalSchedule();
        long unassigned = internalSchedule();
        long others = internalSchedule();
        assign(assigned, profile);
        assign(others, otherProfile);
        var token = session(caller);
        guideGuard(assigned, token).andExpect(status().isOk());
        for (long denied : List.of(unassigned, others, 999999L)) {
            guideGuard(denied, token).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        guideGuard(assigned, session(admin)).andExpect(status().isNotFound());
        jdbc.update("delete from trekking.schedule_guides where schedule_id = ? and guide_id = ?", assigned, profile);
        guideGuard(assigned, token).andExpect(status().isNotFound());
    }

    @Test
    void profileAndRoleRevocationsTakeEffectOnTheSameJwt() throws Exception {
        long caller = account("guide@example.invalid", "guide", "customer");
        long profile = internalGuide(caller);
        long departure = internalSchedule();
        assign(departure, profile);
        var token = session(caller);
        guideGuard(departure, token).andExpect(status().isOk());
        jdbc.update("update trekking.guides set active = false where id = ?", profile);
        guideGuard(departure, token).andExpect(status().isNotFound());
        jdbc.update("update trekking.guides set active = true where id = ?", profile);
        guideGuard(departure, token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'guide'", caller);
        guideGuard(departure, token).andExpect(status().isNotFound());
    }

    @Test
    void accountRoleReplacementRevokesAssignedGuideAccessFromExistingJwt() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long caller = account("guide@example.invalid", "guide", "customer");
        long profile = internalGuide(caller);
        long departure = internalSchedule();
        assign(departure, profile);
        var token = session(caller);
        guideGuard(departure, token).andExpect(status().isOk());
        authPut("/api/v1/admin/accounts/" + caller + "/roles",
                Map.of("roles", List.of("customer"), "reason", "Guide responsibility ended"), session(admin))
                .andExpect(status().isOk());
        guideGuard(departure, token).andExpect(status().isNotFound());
    }

    private ResultActions guideGuard(long scheduleId, IssuedTokens token) throws Exception {
        return authGet("/api/v1/test-authorization/schedules/" + scheduleId, token);
    }

    private void assign(long scheduleId, long guideId) {
        jdbc.update("insert into trekking.schedule_guides(schedule_id, guide_id) values (?, ?)", scheduleId, guideId);
    }

    /** Probes verify shared boundaries; booking/tour business endpoints are added in later phases. */
    @TestComponent
    @RestController
    static class ScopeProbeController {
        private final AuthorizationService authorization;
        ScopeProbeController(AuthorizationService authorization) { this.authorization = authorization; }

        @GetMapping("/api/v1/test-authorization/schedules/{scheduleId}")
        Map<String, Boolean> schedule(@PathVariable long scheduleId) {
            authorization.requireGuideSchedule(scheduleId);
            return Map.of("allowed", true);
        }
    }
}

/** Shared fixtures exercise the real session converter and disposable PostgreSQL/Redis stores. */
abstract class AccountAuthorizationTestSupport extends AuthTestInfrastructure {
    static final String PASSWORD = "AccountTestPassword123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @MockitoBean OtpMailSender mail;

    @BeforeEach
    void resetAccounts() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("auth_test");
        jdbc.execute("truncate trekking.app_accounts restart identity cascade");
        var keys = redis.keys("auth-test:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    long account(String email, String... roles) {
        long id = jdbc.queryForObject("""
                insert into trekking.app_accounts(email, password_hash, full_name, email_verified_at)
                values (?, ?, ?, current_timestamp) returning id
                """, Long.class, email, passwords.encode(PASSWORD), "Test " + email);
        for (String role : roles) jdbc.update("insert into trekking.account_roles(account_id, role) values (?, ?)", id, role);
        return id;
    }

    IssuedTokens session(long id) {
        String email = jdbc.queryForObject("select email from trekking.app_accounts where id = ?", String.class, id);
        return auth.login(new AuthRequests.Login(email, PASSWORD), "127.0.0.1");
    }

    String access(IssuedTokens tokens) { return tokens.response().accessToken(); }

    ResultActions authGet(String path, IssuedTokens tokens) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + access(tokens)));
    }

    ResultActions authPost(String path, Object body, IssuedTokens tokens) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + access(tokens))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    ResultActions authPut(String path, Object body, IssuedTokens tokens) throws Exception {
        return mvc.perform(put(path).header("Authorization", "Bearer " + access(tokens))
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    ResultActions refresh(IssuedTokens tokens) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").with(realCsrf())
                .cookie(new Cookie("trekking_refresh", tokens.refreshToken())));
    }

    ResultActions login(long accountId) throws Exception {
        String email = jdbc.queryForObject("select email from trekking.app_accounts where id = ?", String.class, accountId);
        return mvc.perform(post("/api/v1/auth/login").with(realCsrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    RequestPostProcessor realCsrf() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return request -> {
            var cookies = new ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(List.of(request.getCookies()));
            cookies.add(cookie);
            request.setCookies(cookies.toArray(Cookie[]::new));
            request.addHeader(body.get("headerName").asText(), body.get("token").asText());
            return request;
        };
    }

    long internalGuide(long accountId) {
        return jdbc.queryForObject("insert into trekking.guides(account_id) values (?) returning id", Long.class, accountId);
    }

    long internalSchedule() {
        long tourId = jdbc.queryForObject("insert into trekking.tours(title) values ('Scope test tour') returning id", Long.class);
        return jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id, starts_at, ends_at, capacity,
                    base_price_vnd, deposit_percent, balance_due_at)
                values (?, current_timestamp + interval '10 days', current_timestamp + interval '11 days',
                    10, 100000, 30, current_timestamp + interval '9 days') returning id
                """, Long.class, tourId);
    }
}
