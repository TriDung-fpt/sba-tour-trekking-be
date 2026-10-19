package com.trekking.app;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
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
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    void authorizationRequiresCurrentAuthenticatedSession() throws Exception {
        mvc.perform(get(AUTHORIZATION)).andExpect(status().isUnauthorized());
        mvc.perform(get(AUTHORIZATION).header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(RoleType.class)
    void capabilityResponseSeparatesAllSevenRolesFromGlobalAdminPermissions(RoleType role) throws Exception {
        long caller = account("caller@example.invalid", role.getValue());
        var result = authGet(AUTHORIZATION, session(caller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(caller))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value(role.getValue()))
                .andExpect(jsonPath("$.companies").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsString());
        var permissions = new ArrayList<String>();
        body.get("permissions").forEach(permission -> permissions.add(permission.asText()));
        if (role == RoleType.ADMIN) {
            assertThat(permissions).containsExactlyInAnyOrder("ACCOUNT_READ", "ACCOUNT_BLOCK", "ACCOUNT_ROLES_WRITE",
                    "COMPANY_REVIEW", "POLICY_MANAGE");
        } else {
            assertThat(permissions).isEmpty();
        }
    }

    @Test
    void accountWithoutRolesHasNoCapabilities() throws Exception {
        long caller = account("no-role@example.invalid");
        authGet(AUTHORIZATION, session(caller)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    void currentRoleChangesAreVisibleWithoutIssuingANewAccessToken() throws Exception {
        long caller = account("caller@example.invalid", "admin", "customer");
        var token = session(caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(5));
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'admin'", caller);
        jdbc.update("insert into trekking.account_roles(account_id, role) values (?, 'support_staff')", caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("customer", "support_staff")))
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void accountStatusRevocationStopsAuthorizationResponseForExistingSession() throws Exception {
        long caller = account("caller@example.invalid", "company_manager");
        var token = session(caller);
        authGet(AUTHORIZATION, token).andExpect(status().isOk());
        jdbc.update("update trekking.app_accounts set status = 'disabled' where id = ?", caller);
        authGet(AUTHORIZATION, token).andExpect(status().isUnauthorized());
    }

    @Test
    void companyGuardRequiresBothManagerRoleAndActiveMembership() throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long roleOnly = account("role-only@example.invalid", "company_manager");
        long membershipOnly = account("membership-only@example.invalid", "customer");
        long manager = account("manager@example.invalid", "company_manager");
        long companyA = company(approver, "Company A");
        long companyB = company(approver, "Company B");
        membership(companyA, membershipOnly, "owner", true);
        membership(companyA, manager, "manager", true);
        membership(companyB, manager, "owner", false);
        for (long denied : List.of(roleOnly, membershipOnly)) {
            companyGuard(companyA, Permission.COMPANY_READ, session(denied))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        var token = session(manager);
        for (Permission permission : List.of(Permission.COMPANY_READ, Permission.COMPANY_MANAGE, Permission.COMPANY_OPERATE)) {
            companyGuard(companyA, permission, token).andExpect(status().isOk());
            companyGuard(companyB, permission, token).andExpect(status().isNotFound());
        }
        companyGuard(999999, Permission.COMPANY_READ, token).andExpect(status().isNotFound());
    }

    @Test
    void globalAdminDoesNotBypassCompanyOrGuideOwnership() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long company = company(admin, "Company A");
        long schedule = schedule(company);
        var token = session(admin);
        membership(company, admin, "owner", true);
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isNotFound());
        guideGuard(schedule, token).andExpect(status().isNotFound());
    }

    @Test
    void capabilityCompanyContextsContainOnlyActiveMembershipsAndCurrentStatus() throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager");
        long approved = company(approver, "Approved Company");
        long pending = company(approver, "Pending Company");
        long inactive = company(approver, "Inactive Membership");
        long unrelated = company(approver, "Unrelated Company");
        jdbc.update("update trekking.companies set status = 'pending' where id = ?", pending);
        membership(approved, manager, "owner", true);
        membership(pending, manager, "manager", true);
        membership(inactive, manager, "owner", false);
        var result = authGet(AUTHORIZATION, session(manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.length()").value(2)).andReturn();
        var companies = json.readTree(result.getResponse().getContentAsString()).get("companies");
        var contexts = new java.util.HashMap<Long, List<String>>();
        companies.forEach(context -> contexts.put(context.get("companyId").asLong(),
                List.of(context.get("status").asText(), context.get("memberRole").asText())));
        assertThat(contexts).containsEntry(approved, List.of("approved", "owner"))
                .containsEntry(pending, List.of("pending", "manager"))
                .doesNotContainKeys(inactive, unrelated);
    }

    @Test
    void membershipAndManagerRoleRevocationsTakeEffectOnTheSameJwt() throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager", "customer");
        long company = company(approver, "Company A");
        membership(company, manager, "owner", true);
        var token = session(manager);
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isOk());
        jdbc.update("update trekking.company_members set active = false where account_id = ?", manager);
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isNotFound());
        authGet(AUTHORIZATION, token).andExpect(status().isOk()).andExpect(jsonPath("$.companies").isEmpty());
        jdbc.update("update trekking.company_members set active = true where account_id = ?", manager);
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'company_manager'", manager);
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isNotFound());
    }

    @Test
    void adminRoleReplacementRevokesCompanyManagementFromExistingJwt() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager", "customer");
        long company = company(admin, "Company A");
        membership(company, manager, "owner", true);
        var managerToken = session(manager);
        companyGuard(company, Permission.COMPANY_MANAGE, managerToken).andExpect(status().isOk());
        authPut("/api/v1/admin/accounts/" + manager + "/roles",
                Map.of("roles", List.of("customer"), "reason", "Company responsibility ended"), session(admin))
                .andExpect(status().isOk());
        companyGuard(company, Permission.COMPANY_MANAGE, managerToken).andExpect(status().isNotFound());
        authGet(AUTHORIZATION, managerToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1)).andExpect(jsonPath("$.roles[0]").value("customer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "suspended", "rejected"})
    void nonApprovedCompaniesPermitProfileManagementButDenyOperations(String statusValue) throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long manager = account("manager@example.invalid", "company_manager");
        long company = company(approver, "Company A");
        membership(company, manager, "manager", true);
        var token = session(manager);
        companyGuard(company, Permission.COMPANY_OPERATE, token).andExpect(status().isOk());
        jdbc.update("update trekking.companies set status = ? where id = ?", statusValue, company);
        companyGuard(company, Permission.COMPANY_READ, token).andExpect(status().isOk());
        companyGuard(company, Permission.COMPANY_MANAGE, token).andExpect(status().isOk());
        companyGuard(company, Permission.COMPANY_OPERATE, token).andExpect(status().isNotFound());
    }

    @Test
    void guideMustBeAssignedToTheExactScheduleAndCompany() throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long caller = account("guide@example.invalid", "guide");
        long other = account("other-guide@example.invalid", "guide");
        long companyA = company(approver, "Company A");
        long companyB = company(approver, "Company B");
        long guideA = guide(companyA, caller);
        long guideB = guide(companyB, other);
        long assigned = schedule(companyA);
        long unassigned = schedule(companyA);
        long outsideCompany = schedule(companyB);
        assign(assigned, guideA, companyA);
        assign(outsideCompany, guideB, companyB);
        var token = session(caller);
        guideGuard(assigned, token).andExpect(status().isOk());
        for (long denied : List.of(unassigned, outsideCompany, 999999L)) {
            guideGuard(denied, token).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        jdbc.update("delete from trekking.schedule_guides where schedule_id = ? and guide_id = ?", assigned, guideA);
        guideGuard(assigned, token).andExpect(status().isNotFound());
    }

    @Test
    void guideProfileCompanyAndRoleRevocationsTakeEffectDuringSession() throws Exception {
        long approver = account("admin@example.invalid", "admin");
        long caller = account("guide@example.invalid", "guide", "customer");
        long company = company(approver, "Company A");
        long guide = guide(company, caller);
        long schedule = schedule(company);
        assign(schedule, guide, company);
        var token = session(caller);
        guideGuard(schedule, token).andExpect(status().isOk());
        jdbc.update("update trekking.guides set active = false where id = ?", guide);
        guideGuard(schedule, token).andExpect(status().isNotFound());
        jdbc.update("update trekking.guides set active = true where id = ?", guide);
        guideGuard(schedule, token).andExpect(status().isOk());
        jdbc.update("update trekking.companies set status = 'suspended' where id = ?", company);
        guideGuard(schedule, token).andExpect(status().isNotFound());
        jdbc.update("update trekking.companies set status = 'approved' where id = ?", company);
        guideGuard(schedule, token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'guide'", caller);
        guideGuard(schedule, token).andExpect(status().isNotFound());
    }

    private ResultActions companyGuard(long companyId, Permission permission, IssuedTokens token) throws Exception {
        return authGet("/api/v1/test-authorization/companies/" + companyId + "/" + permission.name(), token);
    }

    private ResultActions guideGuard(long scheduleId, IssuedTokens token) throws Exception {
        return authGet("/api/v1/test-authorization/schedules/" + scheduleId, token);
    }

    private void assign(long scheduleId, long guideId, long companyId) {
        jdbc.update("insert into trekking.schedule_guides(schedule_id, guide_id, company_id) values (?, ?, ?)",
                scheduleId, guideId, companyId);
    }

    /** Test-only HTTP probes exercise shared guards until their consuming business APIs exist. */
    @TestComponent
    @RestController
    static class ScopeProbeController {
        private final AuthorizationService authorization;

        ScopeProbeController(AuthorizationService authorization) { this.authorization = authorization; }

        @GetMapping("/api/v1/test-authorization/companies/{companyId}/{permission}")
        Map<String, Boolean> company(@PathVariable long companyId, @PathVariable Permission permission) {
            authorization.requireCompany(permission, companyId);
            return Map.of("allowed", true);
        }

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

    long company(long approver, String name) {
        return jdbc.queryForObject("""
                insert into trekking.companies(name, status, approved_by, approved_at)
                values (?, 'approved', ?, current_timestamp) returning id
                """, Long.class, name, approver);
    }

    void membership(long companyId, long accountId, String memberRole, boolean active) {
        jdbc.update("insert into trekking.company_members(company_id, account_id, member_role, active) values (?, ?, ?, ?)",
                companyId, accountId, memberRole, active);
    }

    long guide(long companyId, long accountId) {
        return jdbc.queryForObject("insert into trekking.guides(company_id, account_id) values (?, ?) returning id",
                Long.class, companyId, accountId);
    }

    long schedule(long companyId) {
        long tourId = jdbc.queryForObject("insert into trekking.tours(company_id, title) values (?, 'Scope test tour') returning id",
                Long.class, companyId);
        return jdbc.queryForObject("""
                insert into trekking.tour_schedules(tour_id, company_id, starts_at, ends_at, capacity,
                    base_price_vnd, deposit_percent, balance_due_at)
                values (?, ?, current_timestamp + interval '10 days', current_timestamp + interval '11 days',
                    10, 100000, 30, current_timestamp + interval '9 days') returning id
                """, Long.class, tourId, companyId);
    }
}
