package com.trekking.app;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Persistence contracts available in P1; future booking operations are covered when implemented. */
@SpringBootTest
@AutoConfigureMockMvc
class RepositoryCompatibilityIntegrationTests extends AccountAuthorizationTestSupport {
    @Autowired BookingRepository bookings;
    @Autowired ParticipantRepository participants;
    @Autowired AuditLogRepository audits;
    @Autowired RefundRequestRepository refunds;
    @Autowired PlatformTransactionManager transactionManager;
    long customer;
    long otherCustomer;
    long admin;
    long bookingId;

    @BeforeEach
    void fixture() {
        customer = account("customer@example.invalid", "customer");
        otherCustomer = account("other@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        long scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('repository-cancellation', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Cancellation terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('REPOSITORY-BOOKING', 'repository-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '1 day') returning id
                """, Long.class, customer, scheduleId, versionId);
    }

    @Test
    void ownedBookingReadNeverReturnsAnotherCustomersResource() {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            assertThat(bookings.findOwnedForUpdate(bookingId, otherCustomer)).isEmpty();
            assertThat(bookings.findOwnedForUpdate(999999, customer)).isEmpty();
            assertThat(bookings.findOwnedForUpdate(bookingId, customer)).get()
                    .extracting(Booking::getId).isEqualTo(bookingId);
        });
    }

    @Test
    void acceptedPaymentTotalExcludesUnallocatedReceipts() {
        receipt(30000, "accepted", "accepted-first");
        receipt(20000, "accepted", "accepted-second");
        receipt(90000, "pending_review", "unallocated");
        assertThat(bookings.acceptedPaymentTotalVnd(bookingId)).isEqualByComparingTo("50000");
        assertThat(bookings.acceptedPaymentTotalVnd(999999)).isZero();
        assertThat(bookings.hasReview(bookingId)).isFalse();
    }

    @Test
    void participantLookupRequiresBothBookingAndLinkedAccount() {
        long participantId = jdbc.queryForObject("""
                insert into trekking.participants(booking_id, account_id, full_name)
                values (?, ?, 'Attending customer') returning id
                """, Long.class, bookingId, customer);
        jdbc.update("insert into trekking.participants(booking_id, full_name) values (?, 'Unlinked companion')", bookingId);
        assertThat(participants.findByBookingIdAndAccountId(bookingId, customer)).get()
                .extracting(com.trekking.app.features.booking.entity.Participant::getId).isEqualTo(participantId);
        assertThat(participants.findByBookingIdAndAccountId(bookingId, otherCustomer)).isEmpty();
        assertThat(participants.findByBookingIdAndAccountId(999999, customer)).isEmpty();
    }

    @Test
    void auditRepositoryInsertsEvidenceWhichTheDatabaseKeepsImmutable() {
        AuditLog audit = new AuditLog();
        audit.setActorId(admin);
        audit.setAction("QA_REPOSITORY_EVIDENCE");
        audit.setEntityType("booking");
        audit.setEntityId(bookingId);
        audit.setReason("Preserved evidence");
        audit.setDetails("{}");
        audit.setOccurredAt(OffsetDateTime.now());
        AuditLog saved = audits.saveAndFlush(audit);
        assertThat(saved.getId()).isNotNull();
        assertThat(jdbc.queryForObject("select reason from trekking.audit_logs where id = ?", String.class, saved.getId()))
                .isEqualTo("Preserved evidence");
        assertThatThrownBy(() -> jdbc.update("update trekking.audit_logs set reason = 'tampered' where id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from trekking.audit_logs where id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void refundReadAndLockHoldUntilTransactionCompletes() throws Exception {
        long refundId = jdbc.queryForObject("""
                insert into trekking.refund_requests(booking_id, requested_by, reason, requested_amount_vnd, idempotency_key)
                values (?, ?, 'Refund repository test', 10000, 'repository-refund') returning id
                """, Long.class, bookingId, customer);
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                assertThat(refunds.findForUpdate(999999)).isEmpty();
                assertThat(refunds.findForUpdate(refundId)).isPresent();
                try {
                    String state = executor.submit(() -> {
                        try (var connection = jdbc.getDataSource().getConnection();
                             var query = connection.prepareStatement("select id from trekking.refund_requests where id = ? for update nowait")) {
                            query.setLong(1, refundId);
                            query.executeQuery();
                            return "not-locked";
                        } catch (SQLException ex) {
                            return ex.getSQLState();
                        }
                    }).get(10, TimeUnit.SECONDS);
                    assertThat(state).isEqualTo("55P03");
                } catch (Exception ex) {
                    throw new AssertionError("Could not verify refund row lock", ex);
                }
            });
        }
        assertThat(jdbc.queryForObject("select id from trekking.refund_requests where id = ? for update nowait", Long.class, refundId))
                .isEqualTo(refundId);
    }

    private void receipt(long amount, String allocation, String reference) {
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    allocation_status, received_at, verified_by, evidence_url)
                values (?, 'manual', ?, ?, ?, current_timestamp, ?, 'https://example.invalid/payment-evidence')
                """, bookingId, reference, amount, allocation, admin);
    }
}
