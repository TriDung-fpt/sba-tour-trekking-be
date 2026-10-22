package com.trekking.app;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.service.FinancialOperationAuthorizationService;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.statement.entity.CompanyStatement;
import com.trekking.app.features.statement.enums.StatementStatus;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FinancialOperationAuthorizationIntegrationTests.FinanceProbeController.class)
class FinancialOperationAuthorizationIntegrationTests extends AccountAuthorizationTestSupport {
    @Autowired FinancialOperationAuthorizationService finance;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;

    long admin;
    long preparer;
    long approver;
    long recorder;
    long companyId;

    @BeforeEach
    void financeFixtures() {
        admin = account("finance-admin@example.invalid", "admin");
        preparer = account("finance-preparer@example.invalid", "accountant");
        approver = account("finance-approver@example.invalid", "accountant");
        recorder = account("finance-recorder@example.invalid", "accountant");
        companyId = company(admin, "Finance scope company");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void matchingExplicitPermissionAllowsAnIndependentAccountWithoutMutatingTheResource(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        long id = resource(operation);
        authPost(path(operation, id), Map.of(), session(recorder)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from trekking." + operation.table + " where id = ?",
                String.class, id)).isEqualTo(operation.allowedStatus);
        assertThat(jdbc.queryForObject("select count(*) from trekking.customer_refunds", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from trekking.company_transfers", Long.class)).isZero();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void adminAndAccountantRoleAloneDoNotAuthorizeFinancialOperations(Operation operation) throws Exception {
        long id = resource(operation);
        for (long actor : List.of(admin, recorder)) {
            authPost(path(operation, id), Map.of(), session(actor))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        grant(recorder, Permission.FINANCE_READ);
        authPost(path(operation, id), Map.of(), session(recorder))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void revokingPermissionImmediatelyDeniesAnExistingSession(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        long id = resource(operation);
        var token = session(recorder);
        authPost(path(operation, id), Map.of(), token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_financial_permissions where account_id = ?", recorder);
        authPost(path(operation, id), Map.of(), token)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void removingAccountantRoleImmediatelyDeniesAnExistingSession(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        long id = resource(operation);
        var token = session(recorder);
        authPost(path(operation, id), Map.of(), token).andExpect(status().isOk());
        jdbc.update("delete from trekking.account_roles where account_id = ? and role = 'accountant'", recorder);
        authPost(path(operation, id), Map.of(), token).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void missingResourceReturnsNotFoundForAnAuthorizedAccount(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        authPost(path(operation, 999999), Map.of(), session(recorder))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void changingDutiesDoesNotLetTheOriginalPreparerOrRequesterAuthorizeTheirOwnRecord(Operation operation) throws Exception {
        long id = resource(operation);
        grant(preparer, operation.table.equals("company_statements")
                ? Permission.SETTLEMENT_PREPARE : Permission.FINANCE_READ);
        jdbc.update("delete from trekking.account_financial_permissions where account_id = ?", preparer);
        grant(preparer, operation.permission);
        // These privileges may have changed since this account prepared/requested the resource.
        // Persisted actor IDs, rather than today's role names or request fields, enforce separation.
        authPost(path(operation, id), Map.of("preparedBy", recorder, "requestedBy", recorder), session(preparer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FINANCIAL_SEPARATION_REQUIRED"));
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"PAYOUT", "REFUND_RECORDING"})
    void changingDutiesDoesNotLetTheHistoricalApproverRecordThePayment(Operation operation) throws Exception {
        long id = resource(operation);
        grant(approver, operation == Operation.PAYOUT ? Permission.SETTLEMENT_APPROVE : Permission.REFUND_APPROVE);
        jdbc.update("delete from trekking.account_financial_permissions where account_id = ?", approver);
        grant(approver, operation.permission);
        authPost(path(operation, id), Map.of("approvedBy", recorder), session(approver))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FINANCIAL_SEPARATION_REQUIRED"));
    }

    @ParameterizedTest
    @EnumSource(StatementStatus.class)
    void settlementApprovalRequiresDraftAndPayoutRequiresApproved(StatementStatus state) throws Exception {
        long id = statement(state);
        grant(approver, Permission.SETTLEMENT_APPROVE);
        grant(recorder, Permission.PAYOUT_RECORD);
        var approvalResult = authPost(path(Operation.SETTLEMENT_APPROVAL, id), Map.of(), session(approver));
        if (state == StatementStatus.DRAFT) approvalResult.andExpect(status().isOk());
        else approvalResult.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_APPROVAL_NOT_ALLOWED"));
        var payoutResult = authPost(path(Operation.PAYOUT, id), Map.of(), session(recorder));
        if (state == StatementStatus.APPROVED) payoutResult.andExpect(status().isOk());
        else payoutResult.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYOUT_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void refundApprovalRequiresRequestedAndRecordingRequiresApproved(RefundStatus state) throws Exception {
        long id = refund(state, preparer);
        grant(approver, Permission.REFUND_APPROVE);
        grant(recorder, Permission.REFUND_RECORD);
        var approvalResult = authPost(path(Operation.REFUND_APPROVAL, id), Map.of(), session(approver));
        if (state == RefundStatus.REQUESTED) approvalResult.andExpect(status().isOk());
        else approvalResult.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_APPROVAL_NOT_ALLOWED"));
        var recordingResult = authPost(path(Operation.REFUND_RECORDING, id), Map.of(), session(recorder));
        if (state == RefundStatus.APPROVED) recordingResult.andExpect(status().isOk());
        else recordingResult.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_RECORDING_NOT_ALLOWED"));
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {"REFUND_APPROVAL", "REFUND_RECORDING"})
    void systemRequestedRefundDoesNotRequireAFabricatedRequester(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        long id = refund(RefundStatus.valueOf(operation.allowedStatus.toUpperCase(java.util.Locale.ROOT)), null);
        authPost(path(operation, id), Map.of(), session(recorder)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void guardsRequireTheCallersExistingWriteTransaction(Operation operation) {
        assertThatThrownBy(() -> operation.require(finance, 1)).isInstanceOf(IllegalTransactionStateException.class);
        var readOnly = new TransactionTemplate(transactions);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(ignored -> operation.require(finance, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Financial authorization requires a write transaction");
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void resourceLockRemainsHeldUntilTheCallingTransactionEnds(Operation operation) throws Exception {
        grant(recorder, operation.permission);
        long id = resource(operation);
        authenticate(recorder);
        var transaction = new TransactionTemplate(transactions);
        try (var otherConnection = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(ignored -> {
                operation.require(finance, id);
                try {
                    String sqlState = otherConnection.submit(() -> {
                        try (var connection = jdbc.getDataSource().getConnection();
                             var query = connection.prepareStatement("select id from trekking." + operation.table
                                     + " where id = ? for update nowait")) {
                            query.setLong(1, id);
                            query.executeQuery();
                            return "not-locked";
                        } catch (SQLException ex) {
                            return ex.getSQLState();
                        }
                    }).get(10, TimeUnit.SECONDS);
                    assertThat(sqlState).isEqualTo("55P03");
                } catch (Exception ex) {
                    throw new AssertionError("Could not verify the finance resource lock", ex);
                }
            });
        }
        assertThatCode(() -> jdbc.queryForObject("select id from trekking." + operation.table
                + " where id = ? for update nowait", Long.class, id)).doesNotThrowAnyException();
    }

    @Test
    void settlementGuardReloadsAlreadyManagedResourceBeforeCheckingItsState() throws Exception {
        grant(recorder, Permission.SETTLEMENT_APPROVE);
        long id = statement(StatementStatus.DRAFT);
        authenticate(recorder);
        var transaction = new TransactionTemplate(transactions);
        try (var otherConnection = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                assertThat(entities.find(CompanyStatement.class, id).getStatus()).isEqualTo(StatementStatus.DRAFT);
                try {
                    otherConnection.submit(() -> jdbc.update("""
                            update trekking.company_statements set status = 'approved', approved_by = ?,
                                approved_at = current_timestamp where id = ?
                            """, approver, id)).get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
                finance.requireSettlementApproval(id);
            })).isInstanceOfSatisfying(AuthException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("SETTLEMENT_APPROVAL_NOT_ALLOWED"));
        }
    }

    @Test
    void refundGuardReloadsAlreadyManagedResourceBeforeCheckingItsHistoricalActors() throws Exception {
        grant(recorder, Permission.REFUND_APPROVE);
        long id = refund(RefundStatus.REQUESTED, preparer);
        authenticate(recorder);
        var transaction = new TransactionTemplate(transactions);
        try (var otherConnection = Executors.newSingleThreadExecutor()) {
            assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> {
                assertThat(entities.find(RefundRequest.class, id).getRequestedBy()).isEqualTo(preparer);
                try {
                    otherConnection.submit(() -> jdbc.update(
                            "update trekking.refund_requests set requested_by = ? where id = ?", recorder, id))
                            .get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
                finance.requireRefundApproval(id);
            })).isInstanceOfSatisfying(AuthException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("FINANCIAL_SEPARATION_REQUIRED"));
        }
    }

    private void grant(long accountId, Permission permission) {
        jdbc.update("""
                insert into trekking.account_financial_permissions(account_id, permission, granted_by, granted_at)
                values (?, ?, ?, current_timestamp)
                """, accountId, permission.name(), admin);
    }

    private long resource(Operation operation) {
        return switch (operation) {
            case SETTLEMENT_APPROVAL -> statement(StatementStatus.DRAFT);
            case PAYOUT -> statement(StatementStatus.APPROVED);
            case REFUND_APPROVAL -> refund(RefundStatus.REQUESTED, preparer);
            case REFUND_RECORDING -> refund(RefundStatus.APPROVED, preparer);
        };
    }

    private long statement(StatementStatus state) {
        return jdbc.queryForObject("""
                insert into trekking.company_statements(company_id, period_start, period_end, cutoff_at, status,
                    prepared_by, approved_by, approved_at)
                values (?, current_date - 30, current_date, current_timestamp, ?, ?, ?,
                    case when cast(? as bigint) is not null then current_timestamp end) returning id
                """, Long.class, companyId, state.getValue(), preparer,
                state == StatementStatus.DRAFT ? null : approver, state == StatementStatus.DRAFT ? null : approver);
    }

    private long refund(RefundStatus state, Long requester) {
        long policy = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('finance-test-cancellation', 'Finance cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long version = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Finance cancellation policy', current_timestamp) returning id
                """, Long.class, policy);
        long scheduleId = schedule(companyId);
        long booking = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id, company_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('finance-booking', 'finance-booking', ?, ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '1 day') returning id
                """, Long.class, preparer, scheduleId, companyId, version);
        boolean approved = state != RefundStatus.REQUESTED && state != RefundStatus.REJECTED;
        return jdbc.queryForObject("""
                insert into trekking.refund_requests(booking_id, requested_by, reason, requested_amount_vnd,
                    approved_amount_vnd, status, idempotency_key, approved_by, approved_at)
                values (?, ?, 'Finance test refund', 10000, ?, ?, 'finance-refund', ?,
                    case when cast(? as bigint) is not null then current_timestamp end) returning id
                """, Long.class, booking, requester, approved ? 10000L : null, state.getValue(),
                approved ? approver : null, approved ? approver : null);
    }

    private void authenticate(long actor) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(actor, "finance-service-fixture", null), null, List.of()));
    }

    private static String path(Operation operation, long id) {
        return "/api/v1/test-financial-authorization/" + operation.name() + "/" + id;
    }

    enum Operation {
        SETTLEMENT_APPROVAL(Permission.SETTLEMENT_APPROVE, "company_statements", "draft"),
        PAYOUT(Permission.PAYOUT_RECORD, "company_statements", "approved"),
        REFUND_APPROVAL(Permission.REFUND_APPROVE, "refund_requests", "requested"),
        REFUND_RECORDING(Permission.REFUND_RECORD, "refund_requests", "approved");

        final Permission permission;
        final String table;
        final String allowedStatus;

        Operation(Permission permission, String table, String allowedStatus) {
            this.permission = permission;
            this.table = table;
            this.allowedStatus = allowedStatus;
        }

        void require(FinancialOperationAuthorizationService service, long id) {
            switch (this) {
                case SETTLEMENT_APPROVAL -> service.requireSettlementApproval(id);
                case PAYOUT -> service.requirePayout(id);
                case REFUND_APPROVAL -> service.requireRefundApproval(id);
                case REFUND_RECORDING -> service.requireRefundRecording(id);
            }
        }
    }

    @TestComponent
    @RestController
    static class FinanceProbeController {
        private final FinancialOperationAuthorizationService finance;

        FinanceProbeController(FinancialOperationAuthorizationService finance) {
            this.finance = finance;
        }

        @PostMapping("/api/v1/test-financial-authorization/{operation}/{id}")
        @Transactional
        public Map<String, Boolean> authorize(@PathVariable Operation operation, @PathVariable long id) {
            operation.require(finance, id);
            return Map.of("allowed", true);
        }
    }
}
