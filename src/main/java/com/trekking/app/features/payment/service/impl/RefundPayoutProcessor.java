package com.trekking.app.features.payment.service.impl;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundChannel;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import com.trekking.app.features.payment.service.PayOsPayoutGateway;
import com.trekking.app.features.payment.service.PayOsPayoutGateway.PayoutResult;
import com.trekking.app.features.payment.service.impl.RefundServiceImpl.RefundApprovedEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class RefundPayoutProcessor {
    private static final Logger log = LoggerFactory.getLogger(RefundPayoutProcessor.class);
    private final RefundRequestRepository refunds;
    private final CustomerRefundRepository customerRefunds;
    private final BookingRepository bookings;
    private final PayOsPayoutGateway payouts;
    private final RefundDataCrypto crypto;
    private final RefundEmailNotifier email;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RefundPayoutProcessor(RefundRequestRepository refunds, CustomerRefundRepository customerRefunds,
                                 BookingRepository bookings, PayOsPayoutGateway payouts,
                                 RefundDataCrypto crypto, RefundEmailNotifier email, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.refunds = refunds;
        this.customerRefunds = customerRefunds;
        this.bookings = bookings;
        this.payouts = payouts;
        this.crypto = crypto;
        this.email = email;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void approved(RefundApprovedEvent event) { process(event.refundId()); }

    @Scheduled(fixedDelay = 60_000)
    public void reconcile() {
        List<RefundRequest> pending = refunds.findAllByStatusInOrderByCreatedAtAsc(
                List.of(RefundStatus.APPROVED, RefundStatus.PROCESSING));
        pending.forEach(request -> process(request.getId()));
    }

    private void process(long refundId) {
        PayoutInput input;
        try {
            input = prepare(refundId);
            if (input == null) return;
            PayoutResult result = payouts.findByReference(input.referenceId()).orElseGet(() -> {
                return payouts.create(input.referenceId(), input.idempotencyKey(), input.amountVnd(),
                        "Refund " + refundId, input.bankBin(), crypto.decrypt(input.accountCiphertext()));
            });
            FinalNotice notice = record(refundId, result);
            if (notice != null) sendNotice(notice);
        } catch (Exception ex) {
            // Keep PROCESSING and reconcile by reference before retrying; never log bank data.
            log.warn("Refund payout reconciliation deferred; refundId={}", refundId);
        }
    }

    private PayoutInput prepare(long refundId) {
        return transactions.execute(status -> prepareLocked(refundId));
    }

    private PayoutInput prepareLocked(long refundId) {
        RefundRequest refund = refunds.findForUpdate(refundId).orElse(null);
        if (refund == null || (refund.getStatus() != RefundStatus.APPROVED && refund.getStatus() != RefundStatus.PROCESSING)) return null;
        if (!refund.isRecipientVerified() || refund.getPayoutReferenceId() == null
                || refund.getRecipientAccountCiphertext() == null || refund.getApprovedAmountVnd() == null) return null;
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setPayoutLastCheckedAt(OffsetDateTime.now(clock));
        return new PayoutInput(refund.getPayoutReferenceId(), refund.getIdempotencyKey(),
                refund.getApprovedAmountVnd(), refund.getBankBin(), refund.getRecipientAccountCiphertext());
    }

    private FinalNotice record(long refundId, PayoutResult payout) {
        return transactions.execute(status -> recordLocked(refundId, payout));
    }

    private FinalNotice recordLocked(long refundId, PayoutResult payout) {
        RefundRequest refund = refunds.findForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() == RefundStatus.COMPLETED || refund.getStatus() == RefundStatus.FAILED) return null;
        refund.setPayoutId(payout.payoutId());
        refund.setPayoutTransactionReference(payout.transactionReference());
        refund.setPayoutLastCheckedAt(OffsetDateTime.now(clock));
        String state = payout.state() == null ? "PROCESSING" : payout.state().toUpperCase(java.util.Locale.ROOT);
        if (state.equals("SUCCEEDED") || state.equals("COMPLETED")) {
            refund.setStatus(RefundStatus.COMPLETED);
            var booking = bookings.findById(refund.getBookingId()).orElseThrow();
            var transaction = new CustomerRefund();
            transaction.setRefundRequestId(refund.getId());
            transaction.setBookingId(refund.getBookingId());
            transaction.setAmountVnd(refund.getApprovedAmountVnd());
            transaction.setChannel(RefundChannel.PAYOS);
            transaction.setTransactionReference(payout.transactionReference() == null ? payout.payoutId() : payout.transactionReference());
            transaction.setPayoutId(payout.payoutId());
            transaction.setIdempotencyKey(refund.getIdempotencyKey());
            transaction.setRefundedAt(OffsetDateTime.now(clock));
            transaction.setRecordedBy(refund.getApprovedBy());
            transaction.setEvidenceUrl(null);
            customerRefunds.save(transaction);
            clearRecipient(refund);
            return new FinalNotice(booking.getCustomer().getEmail(), booking.getId(), refund.getApprovedAmountVnd(), "completed");
        }
        if (state.equals("FAILED") || state.equals("REJECTED") || state.equals("CANCELLED")) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setDecisionNote("PayOS payout state: " + state);
            Booking booking = bookings.findById(refund.getBookingId()).orElseThrow();
            clearRecipient(refund);
            return new FinalNotice(booking.getCustomer().getEmail(), booking.getId(), refund.getApprovedAmountVnd(), "failed");
        }
        refund.setStatus(RefundStatus.PROCESSING);
        return null;
    }

    private void sendNotice(FinalNotice notice) {
        try { email.send(notice.email(), notice.bookingId(), notice.amountVnd(), notice.status()); }
        catch (Exception ex) { log.warn("Refund status email could not be sent; bookingId={}", notice.bookingId()); }
    }

    private static void clearRecipient(RefundRequest refund) {
        refund.setRecipientNameCiphertext(null);
        refund.setRecipientAccountCiphertext(null);
        refund.setBankBin(null);
    }

    public record PayoutInput(String referenceId, String idempotencyKey, long amountVnd,
                              String bankBin, String accountCiphertext) {}
    public record FinalNotice(String email, long bookingId, long amountVnd, String status) {}
}
