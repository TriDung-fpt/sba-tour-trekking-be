package com.trekking.app;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FinancialPermissionsIntegrationTests.PermissionProbe.class)
class FinancialPermissionsIntegrationTests extends AccountAuthorizationTestSupport {
    @ParameterizedTest
    @EnumSource(value = Permission.class, names = {"FINANCE_READ", "PAYMENT_RECORD", "REFUND_APPROVE", "REFUND_RECORD",
            "SETTLEMENT_PREPARE", "SETTLEMENT_APPROVE", "PAYOUT_RECORD"})
    void explicitGrantIsAuditedAndRevocationTakesEffectOnExistingJwt(Permission permission) throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant");
        var administrator = session(admin);
        var accountant = session(target);
        authGet(probe(permission), accountant).andExpect(status().isForbidden());
        authGet(probe(permission), administrator).andExpect(status().isForbidden());
        authPut(path(target), Map.of("permissions", List.of(permission.name()), "reason", "  Assign duty  "), administrator)
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(target))
                .andExpect(jsonPath("$.permissions[0]").value(permission.name()));
        authGet(path(target), administrator).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value(permission.name()));
        authGet(probe(permission), accountant).andExpect(status().isOk());
        authGet("/api/v1/accounts/me/authorization", accountant).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value(permission.name()));
        assertThat(jdbc.queryForObject("select granted_by from trekking.account_financial_permissions where account_id=?",
                Long.class, target)).isEqualTo(admin);
        assertThat(jdbc.queryForObject("select reason from trekking.audit_logs where entity_id=?", String.class, target))
                .isEqualTo("Assign duty");
        authPut(path(target), Map.of("permissions", List.of(), "reason", "Revoke duty"), administrator)
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions").isEmpty());
        authGet(probe(permission), accountant).andExpect(status().isForbidden());
        assertThat(grants(target)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "company_manager", "guide", "support_staff", "content_staff", "accountant"})
    void onlyAdminMayReadOrReplaceGrants(String role) throws Exception {
        long caller = account("caller@example.invalid", role);
        long target = account("accountant@example.invalid", "accountant");
        var token = session(caller);
        authGet(path(target), token).andExpect(status().isForbidden());
        authPut(path(target), Map.of("permissions", List.of("FINANCE_READ"), "reason", "Forbidden"), token)
                .andExpect(status().isForbidden());
        assertThat(grants(target)).isEmpty();
    }

    @Test
    void rejectsAnonymousUnknownDuplicateNonFinancialAndConflictingGrants() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant");
        var token = session(admin);
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized());
        for (var invalid : List.of(List.of("ADMIN"), List.of("ACCOUNT_READ"), List.of("FINANCE_READ", "FINANCE_READ"),
                List.of("SETTLEMENT_PREPARE", "SETTLEMENT_APPROVE"), List.of("SETTLEMENT_APPROVE", "PAYOUT_RECORD"),
                List.of("REFUND_APPROVE", "REFUND_RECORD"))) {
            authPut(path(target), Map.of("permissions", invalid, "reason", "Invalid set"), token)
                    .andExpect(status().isBadRequest());
        }
        authPut(path(target), Map.of("permissions", List.of("FINANCE_READ"), "reason", "  "), token)
                .andExpect(status().isBadRequest());
        authPut(path(target), Map.of("reason", "Missing list"), token).andExpect(status().isBadRequest());
        assertThat(grants(target)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from trekking.audit_logs", Long.class)).isZero();
    }

    @Test
    void protectsSelfAdminAndMissingTargetsAndRequiresActivatedAccountantForNewGrants() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long otherAdmin = account("other-admin@example.invalid", "admin", "accountant");
        long target = account("customer@example.invalid", "customer");
        var token = session(admin);
        var body = Map.of("permissions", List.of("FINANCE_READ"), "reason", "Assign");
        authPut(path(admin), body, token).andExpect(status().isForbidden());
        authPut(path(otherAdmin), body, token).andExpect(status().isForbidden());
        authPut(path(999999), body, token).andExpect(status().isNotFound());
        authGet(path(999999), token).andExpect(status().isNotFound());
        authPut(path(target), body, token).andExpect(status().isConflict());
        jdbc.update("insert into trekking.account_roles(account_id,role) values (?, 'accountant')", target);
        jdbc.update("update trekking.app_accounts set email_verified_at=null, must_change_password=true where id=?", target);
        authPut(path(target), body, token).andExpect(status().isConflict());
        assertThat(grants(target)).isEmpty();
    }

    @Test
    void removingAccountantRoleDeletesGrantsAndReaddingRoleDoesNotRestoreThem() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant", "customer");
        var token = session(admin);
        var accountant = session(target);
        authPut(path(target), Map.of("permissions", List.of("FINANCE_READ"), "reason", "Assign"), token)
                .andExpect(status().isOk());
        authPut("/api/v1/admin/accounts/" + target + "/roles", Map.of("roles", List.of("customer"), "reason", "Remove role"), token)
                .andExpect(status().isOk());
        assertThat(grants(target)).isEmpty();
        authPut("/api/v1/admin/accounts/" + target + "/roles", Map.of("roles", List.of("customer", "accountant"), "reason", "Restore role"), token)
                .andExpect(status().isOk());
        authGet(probe(Permission.FINANCE_READ), accountant).andExpect(status().isForbidden());
    }

    @Test
    void administratorMayReplaceDutiesAndRevokeBlockedAccounts() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant");
        var token = session(admin);
        authPut(path(target), Map.of("permissions", List.of("SETTLEMENT_PREPARE"), "reason", "Prepare"), token)
                .andExpect(status().isOk());
        authPut(path(target), Map.of("permissions", List.of("SETTLEMENT_APPROVE"), "reason", "Change duty"), token)
                .andExpect(status().isOk());
        assertThat(grants(target)).containsExactly("SETTLEMENT_APPROVE");
        jdbc.update("update trekking.app_accounts set status='blocked',blocked_reason='Test',blocked_by=? where id=?", admin, target);
        authPut(path(target), Map.of("permissions", List.of("FINANCE_READ"), "reason", "Ineligible"), token)
                .andExpect(status().isConflict());
        authPut(path(target), Map.of("permissions", List.of(), "reason", "Revoke blocked"), token)
                .andExpect(status().isOk());
        assertThat(grants(target)).isEmpty();
    }

    @Test
    void auditFailureRollsBackGrantReplacement() throws Exception {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant");
        var token = session(admin);
        authPut(path(target), Map.of("permissions", List.of("SETTLEMENT_PREPARE"), "reason", "Initial"), token)
                .andExpect(status().isOk());
        jdbc.execute("alter table trekking.audit_logs add constraint qa_financial_audit check(reason <> 'fail-finance')");
        try {
            authPut(path(target), Map.of("permissions", List.of("SETTLEMENT_APPROVE"), "reason", "fail-finance"), token)
                    .andExpect(status().isServiceUnavailable());
            assertThat(grants(target)).containsExactly("SETTLEMENT_PREPARE");
        } finally {
            jdbc.execute("alter table trekking.audit_logs drop constraint qa_financial_audit");
        }
    }

    @Test
    void databaseAlsoRejectsIncompatibleDuties() {
        long admin = account("admin@example.invalid", "admin");
        long target = account("accountant@example.invalid", "accountant");
        jdbc.update("insert into trekking.account_financial_permissions(account_id,permission,granted_by) values (?,'SETTLEMENT_PREPARE',?)", target, admin);
        assertThatThrownBy(() -> jdbc.update("insert into trekking.account_financial_permissions(account_id,permission,granted_by) values (?,'PAYOUT_RECORD',?)", target, admin))
                .isInstanceOf(DataAccessException.class);
        assertThat(grants(target)).containsExactly("SETTLEMENT_PREPARE");
    }

    private List<String> grants(long target) {
        return jdbc.queryForList("select permission from trekking.account_financial_permissions where account_id=? order by permission",
                String.class, target);
    }

    private String path(long id) { return "/api/v1/admin/accounts/" + id + "/financial-permissions"; }
    private String probe(Permission permission) { return "/api/v1/test-financial-permission/" + permission.name(); }

    @RestController
    @TestComponent
    static class PermissionProbe {
        private final AuthorizationService authorization;
        PermissionProbe(AuthorizationService authorization) { this.authorization = authorization; }
        @GetMapping("/api/v1/test-financial-permission/{permission}")
        Map<String, Boolean> require(@PathVariable Permission permission) {
            authorization.require(permission);
            return Map.of("allowed", true);
        }
    }
}
