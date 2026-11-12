package com.trekking.app;

import com.trekking.app.features.payment.service.RefundMailSender;
import com.trekking.app.features.payment.service.RefundMailSender.RefundNotice;
import com.trekking.app.features.payment.service.impl.RefundDataCrypto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefundWorkflowIntegrationTests extends AccountAuthorizationTestSupport {
    @MockitoBean RefundMailSender refundMail;
    @Autowired RefundDataCrypto crypto;

    private static final String ACCOUNT_NUMBER = "0123456789";
    private static final Map<String, Object> REFUND_ACCOUNT = Map.of(
            "bankBin", "970436", "bankName", "Vietcombank",
            "accountNumber", ACCOUNT_NUMBER, "accountHolderName", "NGUYEN VAN A");

    long customer;
    long approver;
    long recorder;
    long booking;

    @BeforeEach
    void fixtures() {
        customer = account("refund-customer@example.invalid", "customer");
        approver = account("refund-approver@example.invalid", "admin");
        recorder = account("refund-recorder@example.invalid", "admin");
        long policy = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('refund-test', 'Refund test policy', 'cancellation', ?) returning id
                """, Long.class, approver);
        long version = jdbc.queryForObject("""
                insert into trekking.policy_versions(policy_id, version_number, content, effective_from,
                    approved_by, approved_at, refund_tiers)
                values (?, 1, 'Cancel 7 days early: 100%, 3 days early: 50%', current_timestamp, ?, current_timestamp,
                    '[{"minHoursBeforeDeparture":168,"refundPercent":100},{"minHoursBeforeDeparture":72,"refundPercent":50}]')
                returning id
                """, Long.class, policy, approver);
        long schedule = internalSchedule(); // departs in 10 days -> 100% tier
        jdbc.update("update trekking.tour_schedules set held_seats = 1 where id = ?", schedule);
        booking = jdbc.queryForObject("""
                insert into trekking.bookings(booking_code, idempotency_key, customer_id, schedule_id,
                    participant_count, includes_equipment, base_price_vnd, equipment_fee_vnd, deposit_percent,
                    cancellation_policy_version_id, policy_snapshot, hold_expires_at, balance_due_at)
                values ('REFUNDTEST', 'refund-test-booking', ?, ?, 1, false, 100000, 0, 30, ?, '{}',
                    current_timestamp + interval '1 hour', current_timestamp + interval '9 days') returning id
                """, Long.class, customer, schedule, version);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    allocation_status, received_at)
                values (?, 'payos', 'refund-test-payment', 30000, 'accepted', current_timestamp)
                """, booking);
    }

    @Test
    void cancellationRequestsPolicyRefundAndFinanceCompletesManualTransfer() throws Exception {
        var customerSession = session(customer);
        authGet("/api/v1/bookings/" + booking + "/refund-preview", customerSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.paidAmountVnd").value(30000))
                .andExpect(jsonPath("$.refundPercent").value(100))
                .andExpect(jsonPath("$.estimatedRefundVnd").value(30000))
                .andExpect(jsonPath("$.refundAccountRequired").value(true));

        // A paid booking cannot be cancelled without a destination, and nothing is changed (no email either).
        authPost("/api/v1/bookings/" + booking + "/cancel", Map.of("reason", "Busy"), customerSession)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_REQUIRED"));
        verifyNoInteractions(refundMail);
        assertThat(jdbc.queryForObject("select status from trekking.bookings where id = ?", String.class, booking))
                .isEqualTo("pending_payment");

        var cancelled = authPost("/api/v1/bookings/" + booking + "/cancel",
                Map.of("reason", "Busy", "refundAccount", REFUND_ACCOUNT), customerSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundRequired").value(true))
                .andExpect(jsonPath("$.refund.status").value("requested"))
                .andExpect(jsonPath("$.refund.requestedAmountVnd").value(30000))
                .andExpect(jsonPath("$.refund.accountNumberMasked").value("****6789"))
                .andReturn();
        long refundId = json.readTree(cancelled.getResponse().getContentAsString()).get("refund").get("id").asLong();

        // Bank details are encrypted at rest.
        var stored = jdbc.queryForMap("select recipient_account_ciphertext, recipient_name_ciphertext from trekking.refund_requests where id = ?", refundId);
        assertThat((String) stored.get("recipient_account_ciphertext")).startsWith("v1:").doesNotContain(ACCOUNT_NUMBER);
        assertThat((String) stored.get("recipient_name_ciphertext")).startsWith("v1:").doesNotContain("NGUYEN");

        var approverSession = session(approver);
        authPost("/api/v1/admin/refunds/" + refundId + "/approve", Map.of("approvedAmountVnd", 40000), approverSession)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFUND_AMOUNT_INVALID"));
        authPost("/api/v1/admin/refunds/" + refundId + "/approve", Map.of("approvedAmountVnd", 25000, "note", "Checked"), approverSession)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvedAmountVnd").value(25000));

        // Approver and recorder must be different accounts.
        authPost("/api/v1/admin/refunds/" + refundId + "/manual-transfer", Map.of("transactionReference", "FT001"), approverSession)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FINANCIAL_SEPARATION_REQUIRED"));

        var recorderSession = session(recorder);
        authGet("/api/v1/admin/refunds/" + refundId, recorderSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.accountHolderName").value("NGUYEN VAN A"))
                .andExpect(jsonPath("$.payosPayoutAvailable").value(false));
        authPost("/api/v1/admin/refunds/" + refundId + "/payos-payout", Map.of(), recorderSession)
                .andExpect(status().isServiceUnavailable());
        authPost("/api/v1/admin/refunds/" + refundId + "/manual-transfer",
                Map.of("transactionReference", "FT001", "evidenceUrl", "https://example.invalid/ft001.png"), recorderSession)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.payoutChannel").value("bank_transfer"));

        assertThat(jdbc.queryForObject("select amount_vnd from trekking.customer_refunds where refund_request_id = ?", Long.class, refundId))
                .isEqualTo(25000L);
        var wiped = jdbc.queryForMap("select recipient_account_ciphertext, recipient_name_ciphertext, recipient_account_last4 from trekking.refund_requests where id = ?", refundId);
        assertThat(wiped.get("recipient_account_ciphertext")).isNull();
        assertThat(wiped.get("recipient_name_ciphertext")).isNull();
        assertThat(wiped.get("recipient_account_last4")).isEqualTo("6789");

        // One email per customer-visible status, sent after commit, never with the full account number.
        var notices = ArgumentCaptor.forClass(RefundNotice.class);
        verify(refundMail, times(3)).send(notices.capture());
        assertThat(notices.getAllValues()).extracting(RefundNotice::status)
                .containsExactly("requested", "approved", "completed");
        assertThat(notices.getAllValues()).allSatisfy(notice -> {
            assertThat(notice.email()).isEqualTo("refund-customer@example.invalid");
            assertThat(notice.bookingCode()).isEqualTo("REFUNDTEST");
            assertThat(notice.toString()).doesNotContain(ACCOUNT_NUMBER);
        });
        assertThat(notices.getAllValues().get(2).amountVnd()).isEqualTo(25000L);

        authGet("/api/v1/bookings/" + booking + "/refund", customerSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.approvedAmountVnd").value(25000));
        authGet("/api/v1/admin/refunds?status=completed", recorderSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(refundId));
    }

    @Test
    void refundIsLockedToTheAccountThatPaid() throws Exception {
        jdbc.update("""
                update trekking.customer_payments set payer_bank_bin = '970415', payer_bank_name = 'VietinBank',
                    payer_account_ciphertext = ?, payer_name_ciphertext = ?, payer_account_last4 = '4321'
                where booking_id = ?
                """, crypto.encrypt("9876543210004321", RefundDataCrypto.PAYER_ACCOUNT_FIELD),
                crypto.encrypt("TRAN THI B", RefundDataCrypto.PAYER_NAME_FIELD), booking);
        var customerSession = session(customer);
        authGet("/api/v1/bookings/" + booking + "/refund-preview", customerSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAccountRequired").value(false))
                .andExpect(jsonPath("$.refundAccountLocked").value(true))
                .andExpect(jsonPath("$.lockedBankName").value("VietinBank"))
                .andExpect(jsonPath("$.lockedAccountMasked").value("****4321"));

        // A different destination is rejected and nothing changes.
        authPost("/api/v1/bookings/" + booking + "/cancel",
                Map.of("reason", "Busy", "refundAccount", REFUND_ACCOUNT), customerSession)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_LOCKED"));
        assertThat(jdbc.queryForObject("select status from trekking.bookings where id = ?", String.class, booking))
                .isEqualTo("pending_payment");

        var cancelled = authPost("/api/v1/bookings/" + booking + "/cancel", Map.of("reason", "Busy"), customerSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.accountNumberMasked").value("****4321"))
                .andReturn();
        long refundId = json.readTree(cancelled.getResponse().getContentAsString()).get("refund").get("id").asLong();

        authGet("/api/v1/admin/refunds/" + refundId, session(approver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bankBin").value("970415"))
                .andExpect(jsonPath("$.accountNumber").value("9876543210004321"))
                .andExpect(jsonPath("$.accountHolderName").value("TRAN THI B"))
                .andExpect(jsonPath("$.recipientFromPayment").value(true));
    }

    @Test
    void paymentsFromDifferentAccountsFallBackToCustomerEnteredAccount() throws Exception {
        jdbc.update("""
                update trekking.customer_payments set payer_bank_bin = '970415',
                    payer_account_ciphertext = ?, payer_account_last4 = '4321' where booking_id = ?
                """, crypto.encrypt("9876543210004321", RefundDataCrypto.PAYER_ACCOUNT_FIELD), booking);
        jdbc.update("""
                insert into trekking.customer_payments(booking_id, provider, transaction_reference, amount_vnd,
                    allocation_status, received_at, payer_bank_bin, payer_account_ciphertext, payer_account_last4)
                values (?, 'payos', 'refund-test-payment-2', 10000, 'accepted', current_timestamp, '970436', ?, '1111')
                """, booking, crypto.encrypt("5555555551111", RefundDataCrypto.PAYER_ACCOUNT_FIELD));
        var customerSession = session(customer);
        authGet("/api/v1/bookings/" + booking + "/refund-preview", customerSession).andExpect(status().isOk())
                .andExpect(jsonPath("$.refundAccountRequired").value(true))
                .andExpect(jsonPath("$.refundAccountLocked").value(false));
        var cancelled = authPost("/api/v1/bookings/" + booking + "/cancel",
                Map.of("reason", "Busy", "refundAccount", REFUND_ACCOUNT), customerSession)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.accountNumberMasked").value("****6789"))
                .andReturn();
        long refundId = json.readTree(cancelled.getResponse().getContentAsString()).get("refund").get("id").asLong();
        authGet("/api/v1/admin/refunds/" + refundId, session(approver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientFromPayment").value(false));
    }

    @Test
    void rejectionWipesBankDetailsAndCustomersCannotUseFinanceEndpoints() throws Exception {
        var customerSession = session(customer);
        var cancelled = authPost("/api/v1/bookings/" + booking + "/cancel",
                Map.of("reason", "Busy", "refundAccount", REFUND_ACCOUNT), customerSession)
                .andExpect(status().isOk()).andReturn();
        long refundId = json.readTree(cancelled.getResponse().getContentAsString()).get("refund").get("id").asLong();

        authGet("/api/v1/admin/refunds/" + refundId, customerSession).andExpect(status().isForbidden());
        authPost("/api/v1/admin/refunds/" + refundId + "/approve", Map.of(), customerSession).andExpect(status().isForbidden());

        authPost("/api/v1/admin/refunds/" + refundId + "/reject", Map.of("note", "Wrong account owner"), session(approver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("rejected"));
        assertThat(jdbc.queryForObject("select recipient_account_ciphertext from trekking.refund_requests where id = ?", String.class, refundId))
                .isNull();
    }

    @Test
    void cancellationInsideNoRefundWindowCreatesNoRefundRequest() throws Exception {
        jdbc.update("update trekking.tour_schedules set starts_at = current_timestamp + interval '1 day', "
                + "balance_due_at = current_timestamp + interval '12 hours'");
        authPost("/api/v1/bookings/" + booking + "/cancel", Map.of("reason", "Busy"), session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundRequired").value(false))
                .andExpect(jsonPath("$.refund").doesNotExist());
        assertThat(jdbc.queryForObject("select count(*) from trekking.refund_requests", Long.class)).isZero();
    }

    @Test
    void cancellationPolicyRejectsTiersThatRefundLessForEarlierCancellation() throws Exception {
        long policy = jdbc.queryForObject("""
                insert into trekking.policies(code, title, kind, created_by)
                values ('tier-validation', 'Tier validation', 'cancellation', ?) returning id
                """, Long.class, approver);
        var path = "/api/v1/admin/policies/" + policy + "/versions";
        authPost(path, Map.of("content", "Bad tiers", "effectiveFrom", OffsetDateTime.now().toString(),
                "refundTiers", List.of(Map.of("minHoursBeforeDeparture", 168, "refundPercent", 50),
                        Map.of("minHoursBeforeDeparture", 72, "refundPercent", 100))), session(approver))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REFUND_TIERS_INVALID"));
        authPost(path, Map.of("content", "Good tiers", "effectiveFrom", OffsetDateTime.now().toString(),
                "refundTiers", List.of(Map.of("minHoursBeforeDeparture", 72, "refundPercent", 50),
                        Map.of("minHoursBeforeDeparture", 168, "refundPercent", 100))), session(approver))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundTiers[0].minHoursBeforeDeparture").value(168));
    }
}
