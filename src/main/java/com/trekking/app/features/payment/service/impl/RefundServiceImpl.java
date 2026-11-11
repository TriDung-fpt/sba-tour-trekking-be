package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.account.service.FinancialOperationAuthorizationService;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.dto.request.RefundAccountRequest;
import com.trekking.app.features.payment.dto.request.RefundApproveRequest;
import com.trekking.app.features.payment.dto.request.RefundManualTransferRequest;
import com.trekking.app.features.payment.dto.request.RefundRejectRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundPreviewResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.RefundChannel;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import com.trekking.app.features.payment.service.PayOsPayoutGateway;
import com.trekking.app.features.payment.service.PayOsPayoutGateway.PayoutResult;
import com.trekking.app.features.payment.service.RefundService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.trekking.app.features.payment.service.RefundMailSender.RefundNotice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefundServiceImpl implements RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);
    private static final String NAME_FIELD = "refund_requests.recipient_name";
    private static final String ACCOUNT_FIELD = "refund_requests.recipient_account";
    private static final java.util.List<RefundStatus> OPEN_STATUSES =
            java.util.List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED, RefundStatus.PROCESSING);

    private final RefundRequestRepository refunds;
    private final CustomerRefundRepository customerRefunds;
    private final CustomerPaymentRepository payments;
    private final BookingRepository bookings;
    private final RefundDataCrypto crypto;
    private final PayOsPayoutGateway payouts;
    private final PayOsProperties payOs;
    private final AuthorizationService authorization;
    private final FinancialOperationAuthorizationService financialAuthorization;
    private final AuditLogRepository audits;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;

    public RefundServiceImpl(RefundRequestRepository refunds, CustomerRefundRepository customerRefunds,
                             CustomerPaymentRepository payments, BookingRepository bookings, RefundDataCrypto crypto,
                             PayOsPayoutGateway payouts, PayOsProperties payOs, AuthorizationService authorization,
                             FinancialOperationAuthorizationService financialAuthorization, AuditLogRepository audits,
                             ObjectMapper json, Clock clock, PlatformTransactionManager transactionManager,
                             ApplicationEventPublisher events) {
        this.refunds = refunds;
        this.customerRefunds = customerRefunds;
        this.payments = payments;
        this.bookings = bookings;
        this.crypto = crypto;
        this.payouts = payouts;
        this.payOs = payOs;
        this.authorization = authorization;
        this.financialAuthorization = financialAuthorization;
        this.audits = audits;
        this.json = json;
        this.clock = clock;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- customer side

    @Override
    @Transactional
    public RefundResponse requestOnCancellation(long bookingId, long customerId, String reason,
                                                RefundAccountRequest account) {
        Booking booking = bookings.findById(bookingId).orElseThrow(RefundServiceImpl::notFound);
        Calculation calc = calculate(booking, OffsetDateTime.now(clock));
        if (calc.amountVnd() <= 0) return null;
        if (account == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REFUND_ACCOUNT_REQUIRED",
                    "A bank account is required to receive the refund for this booking");
        }
        if (refunds.existsByBookingIdAndStatusIn(bookingId, OPEN_STATUSES)) {
            throw conflict("REFUND_ALREADY_REQUESTED", "This booking already has an open refund request");
        }
        String accountNumber = account.accountNumber().trim();
        var refund = new RefundRequest();
        refund.setBookingId(bookingId);
        refund.setRequestedBy(customerId);
        refund.setReason(reason.trim());
        refund.setPaidAmountVnd(calc.paidVnd());
        refund.setRefundPercent(calc.percent());
        refund.setHoursBeforeDeparture(calc.hoursBeforeDeparture());
        refund.setRequestedAmountVnd(calc.amountVnd());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setIdempotencyKey("refund-" + UUID.randomUUID());
        refund.setBankBin(account.bankBin().trim());
        refund.setBankName(account.bankName().trim());
        refund.setRecipientNameCiphertext(crypto.encrypt(account.accountHolderName().trim(), NAME_FIELD));
        refund.setRecipientAccountCiphertext(crypto.encrypt(accountNumber, ACCOUNT_FIELD));
        refund.setRecipientAccountLast4(accountNumber.substring(accountNumber.length() - 4));
        refunds.save(refund);
        audit(customerId, refund, "REFUND_REQUESTED", null);
        notifyCustomer(refund);
        return response(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundPreviewResponse previewForCustomer(long bookingId) {
        Booking booking = ownedBooking(bookingId);
        Calculation calc = calculate(booking, OffsetDateTime.now(clock));
        return new RefundPreviewResponse(bookingId, calc.paidVnd(), calc.refundedVnd(), calc.hoursBeforeDeparture(),
                calc.percent(), calc.amountVnd(), calc.tiersConfigured(), calc.amountVnd() > 0);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse latestForCustomer(long bookingId) {
        ownedBooking(bookingId);
        return refunds.findFirstByBookingIdOrderByCreatedAtDescIdDesc(bookingId).map(this::response).orElse(null);
    }

    // ---------------------------------------------------------------- finance side

    @Override
    @Transactional(readOnly = true)
    public Page<RefundResponse> list(String status, Pageable pageable) {
        authorization.require(Permission.FINANCE_READ);
        RefundStatus filter = parseStatus(status);
        var page = filter == null ? refunds.findAllBy(pageable) : refunds.findAllByStatus(filter, pageable);
        return page.map(this::response);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDetailResponse detail(long refundId) {
        authorization.require(Permission.REFUND_APPROVE);
        RefundRequest refund = refunds.findById(refundId).orElseThrow(RefundServiceImpl::notFound);
        Optional<CustomerRefund> paid = customerRefunds.findFirstByRefundRequestId(refundId);
        return new RefundDetailResponse(response(refund), refund.getBooking().getBookingCode(), refund.getReason(),
                refund.getBankBin(), crypto.decrypt(refund.getRecipientAccountCiphertext(), ACCOUNT_FIELD),
                crypto.decrypt(refund.getRecipientNameCiphertext(), NAME_FIELD), refund.getPayoutReferenceId(),
                refund.getPayoutId(), paid.map(CustomerRefund::getTransactionReference).orElse(null),
                paid.map(CustomerRefund::getEvidenceUrl).orElse(null), payOs.isPayoutConfigured());
    }

    @Override
    @Transactional
    public RefundResponse approve(long refundId, RefundApproveRequest request) {
        financialAuthorization.requireRefundApproval(refundId);
        RefundRequest refund = refunds.findForUpdateById(refundId).orElseThrow(RefundServiceImpl::notFound);
        long amount = request == null || request.approvedAmountVnd() == null
                ? refund.getRequestedAmountVnd() : request.approvedAmountVnd();
        if (amount <= 0 || amount > refund.getRequestedAmountVnd()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REFUND_AMOUNT_INVALID",
                    "Approved amount must be positive and must not exceed the policy amount");
        }
        long refundable = sumAcceptedPayments(refund.getBookingId()) - sumRefunds(refund.getBookingId());
        if (amount > refundable) {
            throw conflict("REFUND_EXCEEDS_PAID", "Approved amount exceeds the amount the customer actually paid");
        }
        refund.setApprovedAmountVnd(amount);
        refund.setApprovedBy(authorization.currentAccountId());
        refund.setApprovedAt(OffsetDateTime.now(clock));
        refund.setDecisionNote(blankToNull(request == null ? null : request.note()));
        refund.setStatus(RefundStatus.APPROVED);
        audit(refund.getApprovedBy(), refund, "REFUND_APPROVED", refund.getDecisionNote());
        notifyCustomer(refund);
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse reject(long refundId, RefundRejectRequest request) {
        financialAuthorization.requireRefundApproval(refundId);
        RefundRequest refund = refunds.findForUpdateById(refundId).orElseThrow(RefundServiceImpl::notFound);
        refund.setStatus(RefundStatus.REJECTED);
        refund.setDecisionNote(request.note().trim());
        clearDestination(refund);
        audit(authorization.currentAccountId(), refund, "REFUND_REJECTED", refund.getDecisionNote());
        notifyCustomer(refund);
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse confirmManualTransfer(long refundId, RefundManualTransferRequest request) {
        financialAuthorization.requireRefundRecording(refundId);
        RefundRequest refund = refunds.findForUpdateById(refundId).orElseThrow(RefundServiceImpl::notFound);
        String reference = request.transactionReference().trim();
        if (customerRefunds.existsByChannelAndTransactionReference(RefundChannel.BANK_TRANSFER, reference)) {
            throw conflict("REFUND_TRANSACTION_DUPLICATE", "This bank transaction reference was already recorded");
        }
        long actor = authorization.currentAccountId();
        refund.setPayoutChannel(RefundChannel.BANK_TRANSFER);
        if (request.note() != null && !request.note().isBlank()) refund.setDecisionNote(request.note().trim());
        complete(refund, RefundChannel.BANK_TRANSFER, reference, blankToNull(request.evidenceUrl()), null, actor);
        audit(actor, refund, "REFUND_MANUAL_TRANSFER_RECORDED", reference);
        notifyCustomer(refund);
        return response(refund);
    }

    /**
     * Three short steps so the external call never runs inside a database transaction:
     * (1) lock + mark PROCESSING with a stable reference, (2) call PayOS, (3) record the result.
     * If step 2 fails ambiguously the refund stays PROCESSING and {@link #syncPayOsPayout} reconciles by reference.
     */
    @Override
    public RefundResponse startPayOsPayout(long refundId) {
        payOs.requirePayoutConfigured();
        PayoutInput input = transactions.execute(status -> {
            financialAuthorization.requireRefundRecording(refundId);
            RefundRequest refund = refunds.findForUpdateById(refundId).orElseThrow(RefundServiceImpl::notFound);
            long actor = authorization.currentAccountId();
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setPayoutChannel(RefundChannel.PAYOS);
            refund.setPayoutInitiatedBy(actor);
            refund.setPayoutReferenceId("RF" + refund.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
            refund.setPayoutState("PROCESSING");
            refund.setPayoutLastCheckedAt(OffsetDateTime.now(clock));
            audit(actor, refund, "REFUND_PAYOS_PAYOUT_STARTED", refund.getPayoutReferenceId());
            return new PayoutInput(refund.getPayoutReferenceId(), refund.getIdempotencyKey(),
                    refund.getApprovedAmountVnd(), refund.getBankBin(),
                    crypto.decrypt(refund.getRecipientAccountCiphertext(), ACCOUNT_FIELD));
        });
        PayoutResult result;
        try {
            result = payouts.create(input.referenceId(), input.idempotencyKey(), input.amountVnd(),
                    "Hoan tien RF" + refundId, input.bankBin(), input.accountNumber());
        } catch (RuntimeException ex) {
            // Outcome unknown: never retry blindly. Finance runs sync, which looks the payout up by reference.
            log.warn("PayOS payout call failed; refundId={} will need a sync", refundId);
            return transactions.execute(status -> response(refunds.findById(refundId).orElseThrow()));
        }
        return transactions.execute(status -> applyPayout(refundId, Optional.of(result)));
    }

    @Override
    public RefundResponse syncPayOsPayout(long refundId) {
        authorization.require(Permission.REFUND_RECORD);
        payOs.requirePayoutConfigured();
        String reference = transactions.execute(status -> {
            RefundRequest refund = refunds.findById(refundId).orElseThrow(RefundServiceImpl::notFound);
            if (refund.getStatus() != RefundStatus.PROCESSING || refund.getPayoutChannel() != RefundChannel.PAYOS) {
                throw conflict("REFUND_SYNC_NOT_ALLOWED", "Only a refund with a PayOS payout in progress can be synced");
            }
            return refund.getPayoutReferenceId();
        });
        Optional<PayoutResult> result = payouts.findByReference(reference);
        return transactions.execute(status -> applyPayout(refundId, result));
    }

    private RefundResponse applyPayout(long refundId, Optional<PayoutResult> found) {
        RefundRequest refund = refunds.findForUpdateById(refundId).orElseThrow(RefundServiceImpl::notFound);
        if (refund.getStatus() != RefundStatus.PROCESSING) return response(refund);
        refund.setPayoutLastCheckedAt(OffsetDateTime.now(clock));
        if (found.isEmpty() || found.get().failed()) {
            // Never created or rejected by PayOS: return to APPROVED so finance can retry or transfer manually.
            String state = found.map(PayoutResult::state).orElse("NOT_FOUND");
            refund.setStatus(RefundStatus.APPROVED);
            refund.setPayoutState(state);
            refund.setPayoutReferenceId(null);
            audit(authorization.currentAccountId(), refund, "REFUND_PAYOS_PAYOUT_FAILED", state);
            return response(refund);
        }
        PayoutResult payout = found.get();
        refund.setPayoutId(payout.payoutId());
        refund.setPayoutState(payout.state());
        if (payout.succeeded()) {
            String reference = payout.transactionReference() != null ? payout.transactionReference()
                    : payout.payoutId() != null ? payout.payoutId() : refund.getPayoutReferenceId();
            long recorder = refund.getPayoutInitiatedBy() != null ? refund.getPayoutInitiatedBy()
                    : authorization.currentAccountId();
            complete(refund, RefundChannel.PAYOS, reference, null, payout.payoutId(), recorder);
            audit(authorization.currentAccountId(), refund, "REFUND_PAYOS_PAYOUT_COMPLETED", reference);
            notifyCustomer(refund);
        }
        return response(refund);
    }

    private void complete(RefundRequest refund, RefundChannel channel, String transactionReference,
                          String evidenceUrl, String payoutId, long recordedBy) {
        var transaction = new CustomerRefund();
        transaction.setRefundRequestId(refund.getId());
        transaction.setBookingId(refund.getBookingId());
        transaction.setAmountVnd(refund.getApprovedAmountVnd());
        transaction.setChannel(channel);
        transaction.setTransactionReference(transactionReference);
        transaction.setIdempotencyKey(refund.getIdempotencyKey());
        transaction.setRefundedAt(OffsetDateTime.now(clock));
        transaction.setRecordedBy(recordedBy);
        transaction.setEvidenceUrl(evidenceUrl);
        transaction.setPayoutId(payoutId);
        customerRefunds.save(transaction);
        refund.setStatus(RefundStatus.COMPLETED);
        clearDestination(refund);
    }

    // ---------------------------------------------------------------- calculation

    /** Amount = (accepted payments - refunds already paid) x tier percent, rounded down to whole VND. */
    private Calculation calculate(Booking booking, OffsetDateTime now) {
        long paid = sumAcceptedPayments(booking.getId());
        long refunded = sumRefunds(booking.getId());
        long available = Math.max(0, paid - refunded);
        long hours = Duration.between(now, booking.getSchedule().getStartsAt()).toHours();
        String rawTiers = booking.getCancellationPolicyVersion() == null ? null
                : booking.getCancellationPolicyVersion().getRefundTiers();
        if (rawTiers == null || rawTiers.isBlank()) {
            // Legacy policy without tiers: request the full balance; finance applies the written policy on approval.
            return new Calculation(paid, refunded, hours, null, available, false);
        }
        int percent = hours < 0 ? 0 : Arrays.stream(json.readValue(rawTiers, Tier[].class))
                .sorted(Comparator.comparingInt(Tier::minHoursBeforeDeparture).reversed())
                .filter(tier -> tier.minHoursBeforeDeparture() <= hours)
                .mapToInt(Tier::refundPercent).findFirst().orElse(0);
        // Refund = refundable amount x percent / 100, rounded down to whole VND.
        BigDecimal refundableAmount = BigDecimal.valueOf(available);
        BigDecimal refundRate = BigDecimal.valueOf(percent).divide(BigDecimal.valueOf(100));
        BigDecimal refundAmount = refundableAmount.multiply(refundRate).setScale(0, RoundingMode.DOWN);
        long amount = refundAmount.longValueExact();
        return new Calculation(paid, refunded, hours, percent, amount, true);
    }

    // ---------------------------------------------------------------- helpers

    private Booking ownedBooking(long bookingId) {
        return bookings.findWithScheduleByIdAndCustomerId(bookingId, authorization.currentAccountId())
                .orElseThrow(RefundServiceImpl::notFound);
    }

    private RefundResponse response(RefundRequest refund) {
        return new RefundResponse(refund.getId(), refund.getBookingId(), refund.getStatus().getValue(),
                refund.getPaidAmountVnd() == null ? 0 : refund.getPaidAmountVnd(), refund.getRefundPercent(),
                refund.getHoursBeforeDeparture(), refund.getRequestedAmountVnd(), refund.getApprovedAmountVnd(),
                refund.getBankName(),
                refund.getRecipientAccountLast4() == null ? null : "****" + refund.getRecipientAccountLast4(),
                refund.getPayoutChannel() == null ? null : refund.getPayoutChannel().getValue(),
                refund.getPayoutState(), refund.getDecisionNote(), refund.getApprovedAt(),
                refund.getCreatedAt(), refund.getUpdatedAt());
    }

    /** Queues a customer email; it is sent after commit by {@link RefundNotificationListener}. */
    private void notifyCustomer(RefundRequest refund) {
        Booking booking = bookings.findById(refund.getBookingId()).orElseThrow(RefundServiceImpl::notFound);
        var customer = booking.getCustomer();
        long amount = refund.getApprovedAmountVnd() != null ? refund.getApprovedAmountVnd() : refund.getRequestedAmountVnd();
        boolean showNote = refund.getStatus() == RefundStatus.REJECTED;
        events.publishEvent(new RefundNotice(customer == null ? null : customer.getEmail(),
                customer == null ? null : customer.getFullName(), booking.getBookingCode(),
                refund.getStatus().getValue(), amount,
                refund.getRecipientAccountLast4() == null ? null : "****" + refund.getRecipientAccountLast4(),
                showNote ? refund.getDecisionNote() : null));
    }

    /** Full bank details are only needed until money moves; keep the masked last digits for history. */
    private static void clearDestination(RefundRequest refund) {
        refund.setRecipientNameCiphertext(null);
        refund.setRecipientAccountCiphertext(null);
    }

    private void audit(long actorId, RefundRequest refund, String action, String note) {
        var audit = new AuditLog();
        audit.setActorId(actorId);
        audit.setAction(action);
        audit.setEntityType("refund_request");
        audit.setEntityId(refund.getId());
        audit.setReason(note);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("bookingId", refund.getBookingId());
        details.put("requestedAmountVnd", refund.getRequestedAmountVnd());
        details.put("approvedAmountVnd", refund.getApprovedAmountVnd());
        details.put("status", refund.getStatus().getValue());
        audit.setDetails(json.writeValueAsString(details));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private static RefundStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(RefundStatus.values()).filter(s -> s.getValue().equalsIgnoreCase(value.trim()))
                .findFirst().orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "REFUND_STATUS_INVALID",
                        "Unknown refund status: " + value));
    }

    /** Total money the customer actually paid (accepted payments only). */
    private long sumAcceptedPayments(long bookingId) {
        long total = 0;
        for (CustomerPayment payment : payments.findAllByBookingIdAndAllocationStatus(bookingId, AllocationStatus.ACCEPTED)) {
            total += payment.getAmountVnd();
        }
        return total;
    }

    /** Total money already refunded to the customer. */
    private long sumRefunds(long bookingId) {
        long total = 0;
        for (CustomerRefund refund : customerRefunds.findAllByBookingId(bookingId)) {
            total += refund.getAmountVnd();
        }
        return total;
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Refund or booking was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    record Tier(int minHoursBeforeDeparture, int refundPercent) {}

    private record Calculation(long paidVnd, long refundedVnd, long hoursBeforeDeparture, Integer percent,
                               long amountVnd, boolean tiersConfigured) {}

    private record PayoutInput(String referenceId, String idempotencyKey, long amountVnd, String bankBin,
                               String accountNumber) {}
}
