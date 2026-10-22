package com.trekking.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountAdministrationIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String ADMIN_ACCOUNTS = "/api/v1/admin/accounts/";

    @Test
    void unauthenticatedRequestsCannotReadOrMutateAccounts() throws Exception {
        long target = account("target@example.invalid", "customer");
        mvc.perform(get(ADMIN_ACCOUNTS + target)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN_ACCOUNTS + target + "/block").contentType("application/json")
                .content(json.writeValueAsString(Map.of("reason", "Reported violation"))))
                .andExpect(status().isUnauthorized());
        assertThat(accountStatus(target)).isEqualTo("active");
        assertThat(auditCount(target)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "company_manager", "guide", "support_staff", "content_staff", "accountant"})
    void everyNonAdminRoleIsDeniedAccountAdministration(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        long target = account("target@example.invalid", "customer");
        var token = session(caller);
        authGet(ADMIN_ACCOUNTS + target, token).andExpect(status().isForbidden());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Violation"), token)
                .andExpect(status().isForbidden());
        authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("reason", "Assign support", "roles", List.of("support_staff")), token)
                .andExpect(status().isForbidden());
        assertThat(accountStatus(target)).isEqualTo("active");
        assertThat(auditCount(target)).isZero();
    }

    @Test
    void adminReadsSafeAccountSnapshotAndMissingTargetsAre404() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        authGet(ADMIN_ACCOUNTS + target, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target)).andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.roles[0]").value("customer"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionVersion").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(PASSWORD))));
        authGet(ADMIN_ACCOUNTS + 999999, token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        authPost(ADMIN_ACCOUNTS + 999999 + "/block", Map.of("reason", "Violation"), token)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "9223372036854775808"})
    void invalidAccountIdsReturnTheApiErrorContractWithoutMutation(String invalidId) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        authGet(ADMIN_ACCOUNTS + invalidId, token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
        authPost(ADMIN_ACCOUNTS + invalidId + "/block", Map.of("reason", "Invalid target"), token)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(accountStatus(target)).isEqualTo("active");
        assertThat(auditCount(target)).isZero();
    }

    @Test
    void blockingRequiresReasonRecordsActorAndRevokesAccessRefreshAndLogin() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var adminToken = session(admin);
        var firstDevice = session(target);
        var secondDevice = session(target);
        for (Map<String, ?> invalid : List.of(Map.<String, Object>of(), Map.of("reason", "  "))) {
            authPost(ADMIN_ACCOUNTS + target + "/block", invalid, adminToken).andExpect(status().isBadRequest());
        }
        assertThat(accountStatus(target)).isEqualTo("active");
        assertThat(auditCount(target)).isZero();
        authPost(ADMIN_ACCOUNTS + target + "/block",
                Map.of("reason", "  Confirmed violation  ", "actorId", target, "status", "active"), adminToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.blockedReason").value("Confirmed violation"))
                .andExpect(jsonPath("$.blockedBy").value(admin));
        assertThat(auditCount(target)).isEqualTo(1);
        var audit = jdbc.queryForMap("select actor_id, action, reason, details::text from trekking.audit_logs where entity_id = ?", target);
        assertThat(audit.get("actor_id")).isEqualTo(admin);
        assertThat(audit.get("action")).isEqualTo("ACCOUNT_BLOCKED");
        assertThat(audit.get("reason")).isEqualTo("Confirmed violation");
        var details = json.readTree((String) audit.get("details"));
        assertThat(details.get("before").get("status").asText()).isEqualTo("active");
        assertThat(details.get("after").get("status").asText()).isEqualTo("blocked");
        assertThat(audit.get("details").toString()).doesNotContain(PASSWORD, "passwordHash", "accessToken", "refreshToken");
        for (var device : List.of(firstDevice, secondDevice)) {
            authGet("/api/v1/auth/me", device).andExpect(status().isUnauthorized());
            refresh(device).andExpect(status().isUnauthorized());
        }
        login(target).andExpect(status().isForbidden());
    }

    @Test
    void unblockingRequiresReasonAndDoesNotReviveUntouchedSessions() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var adminToken = session(admin);
        // Neither old token is used while the account is blocked: invalidation must persist in PostgreSQL.
        var oldDevice = session(target);
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Investigation"), adminToken).andExpect(status().isOk());
        authPost(ADMIN_ACCOUNTS + target + "/unblock", Map.of("reason", "  "), adminToken).andExpect(status().isBadRequest());
        authPost(ADMIN_ACCOUNTS + target + "/unblock", Map.of("reason", "  Appeal accepted  "), adminToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.blockedReason").isEmpty()).andExpect(jsonPath("$.blockedBy").isEmpty());
        authGet("/api/v1/auth/me", oldDevice).andExpect(status().isUnauthorized());
        refresh(oldDevice).andExpect(status().isUnauthorized());
        login(target).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select reason from trekking.audit_logs where action = 'ACCOUNT_UNBLOCKED'", String.class))
                .isEqualTo("Appeal accepted");
        assertThat(auditCount(target)).isEqualTo(2);
    }

    @Test
    void invalidTransitionsAndDisabledAccountsDoNotCreateAudit() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        authPost(ADMIN_ACCOUNTS + target + "/unblock", Map.of("reason", "Already active"), token)
                .andExpect(status().isConflict());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Investigation"), token).andExpect(status().isOk());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Duplicate request"), token)
                .andExpect(status().isConflict());
        assertThat(auditCount(target)).isEqualTo(1);
        jdbc.update("update trekking.app_accounts set status = 'disabled' where id = ?", target);
        authPost(ADMIN_ACCOUNTS + target + "/unblock", Map.of("reason", "Cannot reactivate disabled"), token)
                .andExpect(status().isConflict());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Cannot block disabled"), token)
                .andExpect(status().isConflict());
        assertThat(accountStatus(target)).isEqualTo("disabled");
        assertThat(auditCount(target)).isEqualTo(1);
    }

    @Test
    void adminCannotMutateSelfOrAnyAdminAccount() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long otherAdmin = account("other-admin@example.invalid", "customer", "admin");
        var token = session(admin);
        for (long target : List.of(admin, otherAdmin)) {
            for (String action : List.of("block", "unblock")) {
                authPost(ADMIN_ACCOUNTS + target + "/" + action, Map.of("reason", "Invalid action"), token)
                        .andExpect(status().isForbidden());
            }
            authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", List.of("customer"), "reason", "Demote"), token)
                    .andExpect(status().isForbidden());
            assertThat(accountStatus(target)).isEqualTo("active");
            assertThat(auditCount(target)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "company_manager", "guide", "support_staff", "content_staff", "accountant"})
    void adminCanAssignEachFixedNonAdminRole(String role) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", List.of(role), "reason", "Approved responsibility"), session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value(role));
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, target))
                .containsExactly(role);
        assertThat(jdbc.queryForObject("select action from trekking.audit_logs where entity_id = ?", String.class, target))
                .isEqualTo("ACCOUNT_ROLES_CHANGED");
    }

    @Test
    void roleInputCannotEscalateToAdminAndRejectsUnknownEmptyOrDuplicateRoles() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        for (List<String> invalid : List.of(List.<String>of(), List.of("admin"), List.of("customer", "admin"),
                List.of("super_admin"), List.of("CUSTOMER"), List.of("customer", "customer"))) {
            authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", invalid, "reason", "Forbidden input"), token)
                    .andExpect(status().isBadRequest());
        }
        authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", List.of("support_staff"), "reason", "  "), token)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, target))
                .containsExactly("customer");
        assertThat(auditCount(target)).isZero();
    }

    @Test
    void removingAdminRoleTakesEffectOnExistingJwtImmediately() throws Exception {
        long admin = account("admin@example.invalid", "admin", "customer");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        authGet(ADMIN_ACCOUNTS + target, token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'admin'", admin);
        authGet(ADMIN_ACCOUNTS + target, token).andExpect(status().isForbidden());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Stale role claim"), token)
                .andExpect(status().isForbidden());
        assertThat(accountStatus(target)).isEqualTo("active");
        assertThat(auditCount(target)).isZero();
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Immutable evidence"), session(admin)).andExpect(status().isOk());
        assertThatThrownBy(() -> jdbc.update("update trekking.audit_logs set reason = 'tampered' where entity_id = ?", target))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from trekking.audit_logs where entity_id = ?", target))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select reason from trekking.audit_logs where entity_id = ?", String.class, target))
                .isEqualTo("Immutable evidence");
    }

    @Test
    void auditInsertFailureRollsBackBlockingAndSessionVersion() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var adminToken = session(admin);
        var targetToken = session(target);
        long version = jdbc.queryForObject("select session_version from trekking.app_accounts where id = ?", Long.class, target);
        // A disposable PostgreSQL constraint forces the final audit write to fail after account mutation.
        jdbc.execute("alter table trekking.audit_logs add constraint qa_reject_audit check (reason <> 'force-audit-failure')");
        try {
            authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "force-audit-failure"), adminToken)
                    .andExpect(status().isServiceUnavailable());
            assertThat(accountStatus(target)).isEqualTo("active");
            assertThat(jdbc.queryForObject("select session_version from trekking.app_accounts where id = ?", Long.class, target))
                    .isEqualTo(version);
            assertThat(auditCount(target)).isZero();
            authGet("/api/v1/auth/me", targetToken).andExpect(status().isOk());
            refresh(targetToken).andExpect(status().isOk());
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_reject_audit");
        }
    }

    @Test
    void auditInsertFailureRollsBackRoleRemovalAndAddition() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer", "guide");
        var token = session(admin);
        jdbc.execute("alter table trekking.audit_logs add constraint qa_reject_role_audit check (reason <> 'force-role-audit-failure')");
        try {
            authPut(ADMIN_ACCOUNTS + target + "/roles",
                    Map.of("roles", List.of("support_staff", "accountant"), "reason", "force-role-audit-failure"), token)
                    .andExpect(status().isServiceUnavailable());
            assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ? order by role", String.class, target))
                    .containsExactly("customer", "guide");
            assertThat(auditCount(target)).isZero();
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_reject_role_audit");
        }
    }

    private String accountStatus(long id) {
        return jdbc.queryForObject("select status from trekking.app_accounts where id = ?", String.class, id);
    }

    private int auditCount(long id) {
        return jdbc.queryForObject("select count(*) from trekking.audit_logs where entity_type = 'account' and entity_id = ?", Integer.class, id);
    }
}
