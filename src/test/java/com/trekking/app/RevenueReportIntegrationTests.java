package com.trekking.app;

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
class RevenueReportIntegrationTests extends AccountAuthorizationTestSupport {
    private static final String REPORTS = "/api/v1/admin/reports";
    private long admin;
    private long guide;
    private long bookingId;
    private long scheduleId;

    @BeforeEach
    void fixture() {
        long customer = account("customer@example.invalid", "customer");
        admin = account("admin@example.invalid", "admin");
        guide = account("guide@example.invalid", "guide");
        scheduleId = internalSchedule();
        long policyId = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('revenue-report', 'Cancellation', 'cancellation', ?) returning id
                """, Long.class, admin);
        long versionId = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from)
                values (?, 1, 'Terms', current_timestamp - interval '1 day') returning id
                """, Long.class, policyId);
        bookingId = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('REPORT-1', 'report-key', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, customer, scheduleId, versionId);
        payment("RPT-APR", 10000, "accepted", "2027-04-10 09:00:00+07");
        payment("RPT-MAY", 20000, "accepted", "2027-05-20 10:00:00+07");
        payment("RPT-JUN", 30000, "accepted", "2027-06-15 11:00:00+07");
        payment("RPT-MAR", 99999, "accepted", "2027-03-31 23:00:00+07");
        payment("RPT-MAY-PENDING", 77777, "pending_review", "2027-05-21 10:00:00+07");
        long refundRequestId = jdbc.queryForObject("""
                insert into trekking.refund_requests(booking_id, requested_by, reason, requested_amount_vnd,
                    idempotency_key)
                values (?, ?, 'Report refund', 5000, 'report-refund') returning id
                """, Long.class, bookingId, customer);
        jdbc.update("""
                insert into trekking.customer_refunds(refund_request_id, booking_id, original_payment_id,
                    amount_vnd, channel, transaction_reference, idempotency_key, refunded_at, recorded_by,
                    evidence_url)
                values (?, ?, null, 5000, 'bank_transfer', 'RPT-RF', 'report-refund-out',
                    timestamptz '2027-05-05 10:00:00+07', ?, 'https://example.invalid/rf')
                """, refundRequestId, bookingId, admin);
        attempt("RPT-ATT-S1", 10000, "succeeded", "2027-04-11 09:00:00+07");
        attempt("RPT-ATT-F1", 20000, "failed", "2027-05-01 09:00:00+07");
    }

    @Test
    void quarterlyTotalMatchesThreeMonthlyReportsAndYearlySeriesCoversTwelveMonths() throws Exception {
        mvc.perform(get(REPORTS + "/revenue").param("period", "QUARTERLY")
                .param("year", "2027").param("quarter", "2")).andExpect(status().isUnauthorized());
        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=5", session(guide))
                .andExpect(status().isForbidden());

        var adminToken = session(admin);
        long aprilNet = monthly("4", 30, 10000, 0, 10000);
        long mayNet = monthly("5", 31, 20000, 5000, 15000);
        long juneNet = monthly("6", 30, 30000, 0, 30000);

        authGet(REPORTS + "/revenue?period=QUARTERLY&year=2027&quarter=2", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(3))
                .andExpect(jsonPath("$.receivedCount").value(3))
                .andExpect(jsonPath("$.receivedVnd").value(60000))
                .andExpect(jsonPath("$.refundedVnd").value(5000))
                .andExpect(jsonPath("$.netVnd").value(aprilNet + mayNet + juneNet));

        authGet(REPORTS + "/revenue?period=YEARLY&year=2027", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(12))
                .andExpect(jsonPath("$.series[0].bucket").value("2027-01-01"))
                .andExpect(jsonPath("$.series[6].bucket").value("2027-07-01"))
                .andExpect(jsonPath("$.series[6].receivedVnd").value(0))
                .andExpect(jsonPath("$.receivedVnd").value(159999))
                .andExpect(jsonPath("$.refundedVnd").value(5000))
                .andExpect(jsonPath("$.netVnd").value(154999))
                .andExpect(jsonPath("$.previousYearNetVnd").value(org.hamcrest.Matchers.nullValue()));

        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=5&transactionRef=RPT-MAY", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(20000));
        long tourId = jdbc.queryForObject("select tour_id from trekking.tour_schedules where id = ?",
                Long.class, scheduleId);
        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=5&tourId=" + tourId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(20000));
        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=5&method=payos", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(0));

        authGet(REPORTS + "/transactions?period=QUARTERLY&year=2027&quarter=2", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedVnd").value(60000))
                .andExpect(jsonPath("$.refundedVnd").value(5000))
                .andExpect(jsonPath("$.byStatus[?(@.status=='SUCCESS')].count",
                        org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.byStatus[?(@.status=='FAILED')].amountVnd",
                        org.hamcrest.Matchers.hasItem(20000)))
                .andExpect(jsonPath("$.byStatus[?(@.status=='REFUNDED')].amountVnd",
                        org.hamcrest.Matchers.hasItem(5000)));
        authGet(REPORTS + "/transactions?period=QUARTERLY&year=2027&quarter=2&status=SUCCESS", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.length()").value(1))
                .andExpect(jsonPath("$.byStatus[0].status").value("SUCCESS"));

        authGet(REPORTS + "/revenue/export?period=QUARTERLY&year=2027&quarter=2", adminToken)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("revenue_2027_Q2.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("55000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("net_vnd")));
        authGet(REPORTS + "/transactions/export?period=QUARTERLY&year=2027&quarter=2&status=FAILED", adminToken)
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("20000")));
        assertThat(jdbc.queryForObject(
                "select count(*) from trekking.audit_logs where entity_type = 'report' and action = 'REPORT_EXPORTED'",
                Integer.class)).isEqualTo(2);
        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=13", adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    private long monthly(String month, int buckets, long receivedVnd, long refundedVnd, long netVnd)
            throws Exception {
        authGet(REPORTS + "/revenue?period=MONTHLY&year=2027&month=" + month, session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(buckets))
                .andExpect(jsonPath("$.receivedVnd").value(receivedVnd))
                .andExpect(jsonPath("$.refundedVnd").value(refundedVnd))
                .andExpect(jsonPath("$.netVnd").value(netVnd));
        return netVnd;
    }

    private void payment(String reference, long amount, String allocation, String receivedAt) {
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    gateway_fee_vnd, allocation_status, received_at, verified_by, evidence_url)
                values (?, 'manual', ?, ?, 0, ?, ?::timestamptz, ?, ?)
                """, bookingId, reference, amount, allocation, receivedAt, admin,
                "https://example.invalid/" + reference);
    }

    private void attempt(String merchantReference, long amount, String status, String createdAt) {
        jdbc.update("""
                insert into trekking.payment_attempts(booking_id, provider, merchant_reference, purpose,
                    requested_amount_vnd, status, created_at, expires_at)
                values (?, 'manual', ?, 'deposit', ?, ?, ?::timestamptz, timestamptz '2027-12-31 00:00:00+07')
                """, bookingId, merchantReference, amount, status, createdAt);
    }
}
