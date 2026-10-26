package com.trekking.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPaymentMonitoringIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String PAYMENTS = "/api/v1/admin/payments";
    private long admin;
    private long guide;
    private long bookingId;

    @BeforeEach
    void fixture() {
        long customer = account("customer@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
        long scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('admin-payment', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('PAY-1', 'pay-monitor-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, customer, scheduleId, versionId);
        long attemptId = jdbc.queryForObject("""
                insert into trekking.payment_attempts(booking_id, provider, merchant_reference, purpose,
                    requested_amount_vnd, status, expires_at)
                values (?, 'manual', 'MERCHANT-1', 'deposit', 30000, 'succeeded',
                    current_timestamp + interval '1 hour') returning id
                """, Long.class, bookingId);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, attempt_id, provider, transaction_reference,
                    amount_vnd, gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url)
                values (?, ?, 'manual', 'TX-1', 30000, 0, 'accepted', current_timestamp, ?,
                    'https://example.invalid/evidence')
                """, bookingId, attemptId, admin);
        long refundRequestId = jdbc.queryForObject("""
                insert into trekking.refund_requests(booking_id, requested_by, reason, requested_amount_vnd,
                    idempotency_key)
                values (?, ?, 'Customer cancellation', 10000, 'refund-monitor-key') returning id
                """, Long.class, bookingId, customer);
        jdbc.update("""
                insert into trekking.customer_refunds(refund_request_id, booking_id, original_payment_id,
                    amount_vnd, channel, transaction_reference, idempotency_key, refunded_at, recorded_by,
                    evidence_url)
                values (?, ?, null, 10000, 'bank_transfer', 'RF-1', 'refund-monitor-out', current_timestamp, ?,
                    'https://example.invalid/refund-evidence')
                """, refundRequestId, bookingId, admin);
    }

    @Test
    void adminMonitorsAttemptsPaymentsAndRefundsWhileGuideIsDenied() throws Exception {
        mvc.perform(get(PAYMENTS)).andExpect(status().isUnauthorized());
        var guideToken = session(guide);
        authGet(PAYMENTS + "/attempts", guideToken).andExpect(status().isForbidden());
        authGet(PAYMENTS, guideToken).andExpect(status().isForbidden());
        authGet(PAYMENTS + "/refunds", guideToken).andExpect(status().isForbidden());

        var adminToken = session(admin);
        authGet(PAYMENTS + "/attempts", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$.content[0].provider").value("manual"))
                .andExpect(jsonPath("$.content[0].purpose").value("deposit"))
                .andExpect(jsonPath("$.content[0].status").value("succeeded"));
        authGet(PAYMENTS + "/attempts?status=failed", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        authGet(PAYMENTS + "/attempts?status=bogus", adminToken).andExpect(status().isBadRequest());
        authGet(PAYMENTS, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amountVnd").value(30000))
                .andExpect(jsonPath("$.content[0].allocationStatus").value("accepted"))
                .andExpect(jsonPath("$.content[0].transactionReference").value("TX-1"));
        authGet(PAYMENTS + "?allocation=pending_review", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        authGet(PAYMENTS + "/refunds", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amountVnd").value(10000))
                .andExpect(jsonPath("$.content[0].channel").value("bank_transfer"))
                .andExpect(jsonPath("$.content[0].transactionReference").value("RF-1"));
        authGet(PAYMENTS + "/refunds?bookingId=" + bookingId, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }
}
