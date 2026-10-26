package com.trekking.app;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardRevenueIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String DASHBOARD = "/api/v1/admin/dashboard";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
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
                values ('dashboard', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('DASH-1', 'dash-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, customer, scheduleId, versionId);
        long attemptId = jdbc.queryForObject("""
                insert into trekking.payment_attempts(booking_id, provider, merchant_reference, purpose,
                    requested_amount_vnd, status, expires_at)
                values (?, 'manual', 'DASH-MERCHANT', 'deposit', 30000, 'succeeded',
                    timestamptz '2027-03-01 01:00:00+07') returning id
                """, Long.class, bookingId);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, attempt_id, provider, transaction_reference,
                    amount_vnd, gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url)
                values (?, ?, 'manual', 'DASH-TX-IN', 30000, 0, 'accepted',
                    timestamptz '2027-03-01 00:00:00+07', ?, 'https://example.invalid/in')
                """, bookingId, attemptId, admin);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url)
                values (?, 'manual', 'DASH-TX-PENDING', 99999, 0, 'pending_review',
                    timestamptz '2027-03-01 00:00:00+07', ?, 'https://example.invalid/pending')
                """, bookingId, admin);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url)
                values (?, 'manual', 'DASH-TX-OUTSIDE', 55555, 0, 'accepted',
                    timestamptz '2027-04-01 00:00:00+07', ?, 'https://example.invalid/outside')
                """, bookingId, admin);
        long refundRequestId = jdbc.queryForObject("""
                insert into trekking.refund_requests(booking_id, requested_by, reason, requested_amount_vnd,
                    idempotency_key)
                values (?, ?, 'Pending request must not reduce revenue', 77777, 'dash-refund-pending') returning id
                """, Long.class, bookingId, customer);
        jdbc.update("""
                insert into trekking.payment_attempts(booking_id, provider, merchant_reference, purpose,
                    requested_amount_vnd, status, expires_at)
                values (?, 'manual', 'DASH-MERCHANT-RF', 'balance', 10000, 'succeeded',
                    timestamptz '2027-03-15 13:00:00+07')
                """, bookingId);
        jdbc.update("""
                insert into trekking.customer_refunds(refund_request_id, booking_id, original_payment_id,
                    amount_vnd, channel, transaction_reference, idempotency_key, refunded_at, recorded_by,
                    evidence_url)
                values (?, ?, null, 10000, 'bank_transfer', 'DASH-RF', 'dash-refund-out',
                    timestamptz '2027-03-15 12:00:00+07', ?, 'https://example.invalid/refund')
                """, refundRequestId, bookingId, admin);
    }

    @Test
    void netRevenueCountsOnlySuccessfulReceiptsAndCompletedRefundsOverVietnamPeriods() throws Exception {
        mvc.perform(get(DASHBOARD).param("start", "2027-03-01").param("end", "2027-04-01"))
                .andExpect(status().isUnauthorized());
        var guideToken = session(guide);
        authGet(DASHBOARD + "?start=2027-03-01&end=2027-04-01", guideToken).andExpect(status().isForbidden());
        authGet(DASHBOARD + "/export?start=2027-03-01&end=2027-04-01", guideToken)
                .andExpect(status().isForbidden());

        var adminToken = session(admin);
        authGet(DASHBOARD + "?start=2027-03-01&end=2027-04-01", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.receivedCount").value(1))
                .andExpect(jsonPath("$.receivedVnd").value(30000))
                .andExpect(jsonPath("$.refundedCount").value(1))
                .andExpect(jsonPath("$.refundedVnd").value(10000))
                .andExpect(jsonPath("$.netVnd").value(20000))
                .andExpect(jsonPath("$.bookingsByStatus.pending_payment").value(0));
        // End boundary is exclusive and evaluated in Asia/Ho_Chi_Minh: 2027-02-28T17:00Z receipt
        // belongs to March in Vietnam, not to the February period.
        authGet(DASHBOARD + "?start=2027-02-01&end=2027-03-01", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(0))
                .andExpect(jsonPath("$.netVnd").value(0));
        authGet(DASHBOARD + "?start=2027-03-01&end=2027-03-02", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(30000))
                .andExpect(jsonPath("$.refundedVnd").value(0));
        authGet(DASHBOARD + "?start=2027-04-01&end=2027-03-01", adminToken).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        String today = LocalDate.now(VN).toString();
        String yesterday = LocalDate.now(VN).minusDays(1).toString();
        String tomorrow = LocalDate.now(VN).plusDays(1).toString();
        authGet(DASHBOARD + "?start=" + yesterday + "&end=" + tomorrow, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus.pending_payment").value(1));
        authGet(DASHBOARD + "?start=" + today + "&end=" + tomorrow, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus.pending_payment").value(1));

        authGet(DASHBOARD + "/export?start=2027-03-01&end=2027-04-01", adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("dashboard_2027-03-01_2027-04-01.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("received_vnd")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("20000")));
        assertThat(jdbc.queryForObject(
                "select count(*) from trekking.audit_logs where entity_type = 'report' and action = 'REPORT_EXPORTED'",
                Integer.class)).isEqualTo(1);
    }
}
