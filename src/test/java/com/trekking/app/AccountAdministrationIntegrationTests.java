package com.trekking.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    @ValueSource(strings = {"customer", "guide"})
    void everyNonAdminRoleIsDeniedAccountAdministration(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        long target = account("target@example.invalid", "customer");
        var token = session(caller);
        authGet(ADMIN_ACCOUNTS + target, token).andExpect(status().isForbidden());
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Violation"), token)
                .andExpect(status().isForbidden());
        authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("reason", "Assign guide", "roles", List.of("guide")), token)
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
    void adminCannotBlockUnblockOrDemoteSelf() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        var token = session(admin);
        for (String action : List.of("block", "unblock")) {
            authPost(ADMIN_ACCOUNTS + admin + "/" + action, Map.of("reason", "Invalid action"), token)
                    .andExpect(status().isForbidden());
        }
        authPut(ADMIN_ACCOUNTS + admin + "/roles", Map.of("roles", List.of("customer"), "reason", "Demote"), token)
                .andExpect(status().isForbidden());
        assertThat(accountStatus(admin)).isEqualTo("active");
        assertThat(auditCount(admin)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "guide"})
    void nonAdminRolesAreDeniedListUpdateAndAuditLogEndpoints(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        long target = account("target@example.invalid", "customer");
        var token = session(caller);
        authGet("/api/v1/admin/accounts", token).andExpect(status().isForbidden());
        authPut(ADMIN_ACCOUNTS + target, Map.of("fullName", "Renamed"), token).andExpect(status().isForbidden());
        authGet("/api/v1/admin/audit-logs", token).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select full_name from trekking.app_accounts where id = ?", String.class, target))
                .isEqualTo("Test target@example.invalid");
        assertThat(auditCount(target)).isZero();
    }

    @Test
    void adminListsFiltersUpdatesProfileWithAuditAndCannotUpdateSelf() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("target@example.invalid", "customer");
        var token = session(admin);
        authGet("/api/v1/admin/accounts?role=CUSTOMER&query=target", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(target))
                .andExpect(jsonPath("$.content[0].roles[0]").value("customer"));
        authGet("/api/v1/admin/accounts?role=GUIDE", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        authPut(ADMIN_ACCOUNTS + target, Map.of("fullName", "  Updated Name  ", "phone", "0900000001"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("0900000001"));
        authPut(ADMIN_ACCOUNTS + admin, Map.of("fullName", "Self rename"), token)
                .andExpect(status().isForbidden());
        assertThat(auditCount(target)).isEqualTo(1);
        authGet("/api/v1/admin/audit-logs?entityType=account&entityId=" + target, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("ACCOUNT_UPDATED"))
                .andExpect(jsonPath("$.content[0].actorId").value(admin))
                .andExpect(jsonPath("$.content[0].reason").value("Profile updated"));
        authGet("/api/v1/admin/audit-logs?action=ACCOUNT_UPDATED", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void administratorCanBlockUnblockAndDemoteAnotherAdministrator() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("other-admin@example.invalid", "customer", "admin");
        var token = session(admin);
        var oldTargetToken = session(target);
        authPost(ADMIN_ACCOUNTS + target + "/block", Map.of("reason", "Internal access revoked"), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("blocked"));
        authGet(ADMIN_ACCOUNTS + admin, oldTargetToken).andExpect(status().isUnauthorized());
        refresh(oldTargetToken).andExpect(status().isUnauthorized());
        authPost(ADMIN_ACCOUNTS + target + "/unblock", Map.of("reason", "Access reviewed"), token)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        authGet(ADMIN_ACCOUNTS + admin, oldTargetToken).andExpect(status().isUnauthorized());
        var renewedTargetToken = session(target);
        authGet(ADMIN_ACCOUNTS + admin, renewedTargetToken).andExpect(status().isOk());
        authPut(ADMIN_ACCOUNTS + target + "/roles",
                Map.of("roles", List.of("customer", "guide"), "reason", "Administration responsibility ended"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("customer", "guide")));
        authGet(ADMIN_ACCOUNTS + admin, renewedTargetToken).andExpect(status().isForbidden());
        assertThat(accountStatus(admin)).isEqualTo("active");
        assertThat(jdbc.queryForList("select role from trekking.account_roles where account_id = ?", String.class, admin))
                .containsExactly("admin");
        assertThat(auditCount(target)).isEqualTo(3);
    }

    @Test
    void concurrentCrossBlockingLeavesOneActiveAdministratorAndOneAuditedMutation() throws Exception {
        long first = account("first-admin@example.invalid", "admin");
        long second = account("second-admin@example.invalid", "admin");
        var firstToken = session(first);
        var secondToken = session(second);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> firstBlocksSecond = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Cross-block start timed out");
                return authPost(ADMIN_ACCOUNTS + second + "/block", Map.of("reason", "Revoke second admin"), firstToken)
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> secondBlocksFirst = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Cross-block start timed out");
                return authPost(ADMIN_ACCOUNTS + first + "/block", Map.of("reason", "Revoke first admin"), secondToken)
                        .andReturn().getResponse().getStatus();
            };
            var firstResult = workers.submit(firstBlocksSecond);
            var secondResult = workers.submit(secondBlocksFirst);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from trekking.app_accounts a
                join trekking.account_roles r on r.account_id = a.id
                where r.role = 'admin' and a.status = 'active'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs where action = 'ACCOUNT_BLOCKED'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select a.status from trekking.audit_logs log
                join trekking.app_accounts a on a.id = log.actor_id where log.action = 'ACCOUNT_BLOCKED'
                """, String.class)).isEqualTo("active");
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "guide"})
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
                List.of("super_admin"), List.of("company_manager"), List.of("accountant"), List.of("support_staff"),
                List.of("content_staff"), List.of("CUSTOMER"), List.of("customer", "customer"))) {
            authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", invalid, "reason", "Forbidden input"), token)
                    .andExpect(status().isBadRequest());
        }
        authPut(ADMIN_ACCOUNTS + target + "/roles", Map.of("roles", List.of("guide"), "reason", "  "), token)
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
                    Map.of("roles", List.of("customer"), "reason", "force-role-audit-failure"), token)
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
