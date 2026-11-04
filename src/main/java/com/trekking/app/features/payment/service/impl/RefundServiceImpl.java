package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.account.service.FinancialOperationAuthorizationService;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.request.RefundRuleTierRequest;
import com.trekking.app.features.payment.dto.request.RefundSubmissionRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import com.trekking.app.features.payment.config.PayOsProperties;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import com.trekking.app.features.payment.service.RefundService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefundServiceImpl implements RefundService {
    private final RefundRequestRepository refunds;
    private final CustomerPaymentRepository payments;
    private final CustomerRefundRepository customerRefunds;
    private final BookingRepository bookings;
    private final RefundDataCrypto crypto;
    private final AuthorizationService authorization;
    private final FinancialOperationAuthorizationService financialAuthorization;
    private final AuditLogRepository audits;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final Clock clock;
    private final PayOsProperties payOs;

    public RefundServiceImpl(RefundRequestRepository refunds, CustomerPaymentRepository payments,
                             CustomerRefundRepository customerRefunds, BookingRepository bookings,
                             RefundDataCrypto crypto, AuthorizationService authorization,
                             FinancialOperationAuthorizationService financialAuthorization,
                             AuditLogRepository audits, ApplicationEventPublisher events,
                             ObjectMapper json, Clock clock, PayOsProperties payOs) {
        this.refunds = refunds;
        this.payments = payments;
        this.customerRefunds = customerRefunds;
        this.bookings = bookings;
        this.crypto = crypto;
        this.authorization = authorization;
        this.financialAuthorization = financialAuthorization;
        this.audits = audits;
        this.events = events;
        this.json = json;
        this.clock = clock;
        this.payOs = payOs;
    }

    @Override
    @Transactional
    public RefundResponse requestOnCancellation(Booking booking, long customerId, String reason,
                                                RefundSubmissionRequest destination) {
        if (destination == null) return null;
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!booking.getSchedule().getStartsAt().isAfter(now)) throw conflict("REFUND_WINDOW_CLOSED", "Refund requests must be submitted before tour departure");
        long paid = payments.acceptedTotal(booking.getId()).longValueExact();
        if (paid <= 0) throw conflict("REFUND_PAYMENT_REQUIRED", "A refund requires an accepted payment");
        var snapshot = booking.getRefundRulesSnapshot();
        if (snapshot == null || snapshot.isBlank()) throw conflict("REFUND_RULES_NOT_CONFIGURED", "No refund rules were saved for this booking");
        long alreadyRefunded = customerRefunds.refundedTotal(booking.getId()).longValueExact();
        long committed = refunds.committedTotal(booking.getId()).longValueExact();
        long available = paid - alreadyRefunded - committed;
        if (available <= 0) throw conflict("NO_REFUNDABLE_BALANCE", "This booking has no refundable balance");
        long hoursBeforeDeparture = Duration.between(now, booking.getSchedule().getStartsAt()).toHours();
        var tier = rules(snapshot).stream().sorted(Comparator.comparingInt(RefundRuleTierRequest::minimumHoursBeforeDeparture).reversed())
                .filter(rule -> rule.minimumHoursBeforeDeparture() <= hoursBeforeDeparture).findFirst()
                .orElseThrow(() -> conflict("REFUND_RULES_INVALID", "No refund rule matches this booking"));
        long amount = BigDecimal.valueOf(available).multiply(tier.refundPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValueExact();
        if (amount <= 0) throw conflict("REFUND_NOT_ELIGIBLE", "The applicable refund rule does not allow a refund");

        var refund = new RefundRequest();
        refund.setBookingId(booking.getId());
        refund.setRequestedBy(customerId);
        refund.setReason(reason.trim());
        refund.setRequestedAmountVnd(amount);
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setIdempotencyKey("refund-" + UUID.randomUUID());
        refund.setBankBin(destination.bankBin());
        refund.setRecipientNameCiphertext(crypto.encrypt(destination.accountHolderName().trim()));
        refund.setRecipientAccountCiphertext(crypto.encrypt(destination.accountNumber()));
        refund.setRecipientAccountLast4(destination.accountNumber().substring(destination.accountNumber().length() - 4));
        refund.setRecipientVerified(false);
        return response(refunds.save(refund));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse forCustomerBooking(long bookingId) {
        long customerId = authorization.currentAccountId();
        var booking = bookings.findWithScheduleByIdAndCustomerId(bookingId, customerId)
                .orElseThrow(RefundServiceImpl::notFound);
        return refunds.findByBookingId(booking.getId()).map(this::response).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> listForFinance() {
        authorization.require(Permission.FINANCE_READ);
        return refunds.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDetailResponse detailForFinance(long refundId) {
        authorization.require(Permission.REFUND_APPROVE);
        return refunds.findForUpdate(refundId).map(this::detail).orElseThrow(RefundServiceImpl::notFound);
    }

    @Override
    @Transactional
    public RefundResponse approve(long refundId) {
        payOs.requirePayoutConfigured();
        financialAuthorization.requireRefundApproval(refundId);
        var refund = refunds.findForUpdate(refundId).orElseThrow(RefundServiceImpl::notFound);
        refund.setApprovedAmountVnd(refund.getRequestedAmountVnd());
        refund.setApprovedBy(authorization.currentAccountId());
        refund.setApprovedAt(OffsetDateTime.now(clock));
        refund.setRecipientVerified(true); // Approval is the finance user's manual destination verification.
        refund.setPayoutReferenceId("refund-" + refund.getId() + "-" + UUID.randomUUID());
        refund.setStatus(RefundStatus.APPROVED);
        audit(refund, "REFUND_APPROVED", null);
        events.publishEvent(new RefundApprovedEvent(refund.getId()));
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse reject(long refundId, RefundDecisionRequest request) {
        financialAuthorization.requireRefundApproval(refundId);
        var refund = refunds.findForUpdate(refundId).orElseThrow(RefundServiceImpl::notFound);
        refund.setStatus(RefundStatus.REJECTED);
        refund.setDecisionNote(blankToNull(request.note()));
        audit(refund, "REFUND_REJECTED", refund.getDecisionNote());
        return response(refund);
    }

    private List<RefundRuleTierRequest> rules(String value) {
        return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, RefundRuleTierRequest.class));
    }

    private RefundResponse response(RefundRequest refund) {
        return new RefundResponse(refund.getId(), refund.getBookingId(), refund.getRequestedAmountVnd(),
                refund.getApprovedAmountVnd(), refund.getStatus().getValue(), refund.getRecipientAccountLast4(),
                refund.isRecipientVerified() ? "verified" : "manual_review", refund.getCreatedAt(), refund.getPayoutReferenceId());
    }

    private RefundDetailResponse detail(RefundRequest refund) {
        return new RefundDetailResponse(refund.getId(), refund.getBookingId(), refund.getRequestedAmountVnd(),
                refund.getApprovedAmountVnd(), refund.getStatus().getValue(), refund.getReason(), refund.getBankBin(),
                refund.getRecipientNameCiphertext() == null ? null : crypto.decrypt(refund.getRecipientNameCiphertext()),
                refund.getRecipientAccountCiphertext() == null ? null : crypto.decrypt(refund.getRecipientAccountCiphertext()),
                refund.isRecipientVerified() ? "verified" : "manual_review", refund.getCreatedAt(),
                refund.getPayoutReferenceId(), refund.getPayoutId(), refund.getPayoutTransactionReference());
    }

    private void audit(RefundRequest refund, String action, String note) {
        var audit = new AuditLog();
        audit.setActorId(authorization.currentAccountId());
        audit.setAction(action);
        audit.setEntityType("refund_request");
        audit.setEntityId(refund.getId());
        audit.setReason(note);
        audit.setDetails(json.writeValueAsString(Map.of("bookingId", refund.getBookingId(), "amountVnd", refund.getRequestedAmountVnd())));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Refund request was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    public record RefundApprovedEvent(long refundId) {}
}
