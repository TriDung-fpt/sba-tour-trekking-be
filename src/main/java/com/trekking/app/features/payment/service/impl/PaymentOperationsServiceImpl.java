package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.common.idempotency.AuditedIdempotency;
import com.trekking.app.core.common.validation.EvidenceUrl;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.payment.dto.request.CreateRefundRequest;
import com.trekking.app.features.payment.dto.request.ManualPaymentRequest;
import com.trekking.app.features.payment.dto.request.RecordRefundRequest;
import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.request.VerifyPaymentRequest;
import com.trekking.app.features.payment.dto.response.AdminPaymentPage;
import com.trekking.app.features.payment.dto.response.AdminRefundPage;
import com.trekking.app.features.payment.dto.response.PaymentVerificationResponse;
import com.trekking.app.features.payment.dto.response.RefundActionResponse;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.CustomerRefund;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentStatus;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.PaymentAttemptRepository;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import com.trekking.app.features.statement.enums.ItemKind;
import com.trekking.app.features.statement.repository.CompanyStatementItemRepository;
import com.trekking.app.features.payment.service.PaymentOperationsService;
import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentOperationsServiceImpl implements PaymentOperationsService {
    private final CustomerPaymentRepository payments;
    private final CustomerRefundRepository refunds;
    private final RefundRequestRepository requests;
    private final PaymentAttemptRepository attempts;
    private final BookingRepository bookings;
    private final TourScheduleRepository schedules;
    private final AuditedIdempotency idempotency;
    private final CompanyStatementItemRepository statementItems;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;

    public PaymentOperationsServiceImpl(CustomerPaymentRepository payments, CustomerRefundRepository refunds,
            RefundRequestRepository requests, PaymentAttemptRepository attempts, BookingRepository bookings,
            TourScheduleRepository schedules, AuditedIdempotency idempotency,
            CompanyStatementItemRepository statementItems, EntityManager entityManager, ObjectMapper mapper) {
        this.payments = payments; this.refunds = refunds; this.requests = requests; this.attempts = attempts;
        this.bookings = bookings; this.schedules = schedules; this.idempotency = idempotency;
        this.statementItems = statementItems;
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPaymentPage pendingReview(int page, int size) {
        validatePage(page, size);
        var result = payments.findAllByAllocationStatus(AllocationStatus.PENDING_REVIEW,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt")));
        var items = result.getContent().stream().map(p -> {
            var booking = p.getBooking();
            var attempt = p.getAttempt();
            return new AdminPaymentPage.Item(p.getId(), booking.getId(), booking.getBookingCode(),
                    p.getProvider().getValue(), p.getTransactionReference(), p.getAmountVnd(), p.getReceivedAt(),
                    attempt == null ? null : new AdminPaymentPage.Attempt(attempt.getPurpose().getValue(), attempt.getRequestedAmountVnd()));
        }).toList();
        return new AdminPaymentPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public PaymentVerificationResponse verify(long adminId, long paymentId, String key, VerifyPaymentRequest request) {
        var normalized = new VerifyPaymentRequest(EvidenceUrl.require(request.evidenceUrl()), request.reason().trim());
        var prior = idempotency.begin("ADMIN_PAYMENT_VERIFIED", key, new Object[]{paymentId, normalized});
        if (prior.isPresent()) return paymentResponse(payments.findById(prior.get().getEntityId()).orElseThrow());
        CustomerPayment payment = payments.lockById(paymentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
        if (payment.getAllocationStatus() != AllocationStatus.PENDING_REVIEW)
            throw error(HttpStatus.CONFLICT, "PAYMENT_ALREADY_RESOLVED", "Payment is already reconciled");
        Booking booking = bookings.lockById(payment.getBookingId()).orElseThrow();
        TourSchedule schedule = schedules.findByIdForBooking(booking.getScheduleId()).orElseThrow();
        if (!canAccept(booking, schedule, payment.getAmountVnd(), payment.getAttempt() == null ? null : payment.getAttempt().getPurpose()))
            throw error(HttpStatus.CONFLICT, "PAYMENT_NOT_ALLOCATABLE", "Payment amount or booking state cannot be reconciled safely");
        acceptPayment(payment, booking, schedule, adminId, EvidenceUrl.require(request.evidenceUrl()));
        payments.save(payment);
        audit(adminId, "ADMIN_PAYMENT_VERIFIED", "customer_payment", paymentId, request.reason(), key,
                new Object[]{paymentId, normalized});
        return paymentResponse(payment);
    }

    @Override
    @Transactional
    public PaymentVerificationResponse manualPayment(long adminId, long bookingId, String key, ManualPaymentRequest request) {
        var normalized = new ManualPaymentRequest(request.purpose(), request.amountVnd(), request.transactionReference().trim(),
                request.receivedAt(), EvidenceUrl.require(request.evidenceUrl()), request.reason().trim());
        var prior = idempotency.begin("ADMIN_MANUAL_PAYMENT_RECORDED", key, new Object[]{bookingId, normalized});
        if (prior.isPresent()) return paymentResponse(payments.findById(prior.get().getEntityId()).orElseThrow());
        Booking booking = bookings.lockById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        TourSchedule schedule = schedules.findByIdForBooking(booking.getScheduleId()).orElseThrow();
        if (payments.findByProviderAndTransactionReference(PaymentProvider.MANUAL, normalized.transactionReference()).isPresent())
            throw error(HttpStatus.CONFLICT, "PAYMENT_REFERENCE_EXISTS", "This bank reference was already recorded");
        CustomerPayment payment = new CustomerPayment();
        payment.setBookingId(bookingId); payment.setProvider(PaymentProvider.MANUAL);
        payment.setTransactionReference(normalized.transactionReference()); payment.setAmountVnd(normalized.amountVnd());
        payment.setGatewayFeeVnd(0L); payment.setReceivedAt(normalized.receivedAt()); payment.setVerifiedBy(adminId);
        payment.setEvidenceUrl(normalized.evidenceUrl()); payment.setAllocationStatus(AllocationStatus.PENDING_REVIEW);
        payments.saveAndFlush(payment);
        if (canAccept(booking, schedule, payment.getAmountVnd(), normalized.purpose()))
            acceptPayment(payment, booking, schedule, adminId, normalized.evidenceUrl());
        payments.save(payment);
        audit(adminId, "ADMIN_MANUAL_PAYMENT_RECORDED", "customer_payment", payment.getId(), normalized.reason(), key,
                new Object[]{bookingId, normalized});
        return paymentResponse(payment);
    }

    @Override
    @Transactional
    public RefundActionResponse requestRefund(long customerId, long bookingId, String key, CreateRefundRequest request) {
        var normalized = new CreateRefundRequest(request.reason().trim());
        var prior = idempotency.begin("CUSTOMER_REFUND_REQUESTED", key, new Object[]{customerId, bookingId, normalized});
        if (prior.isPresent()) return refundResponse(requests.findById(prior.get().getEntityId()).orElseThrow());
        Booking booking = bookings.lockCustomerBooking(bookingId, customerId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        lockStatementCompany(booking.getCompanyId());
        if (booking.getStatus() != BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.EXPIRED)
            throw error(HttpStatus.CONFLICT, "REFUND_NOT_AVAILABLE", "A refund request requires a cancelled or expired booking");
        if (statementItems.existsByBookingIdAndKind(bookingId, ItemKind.SALE))
            throw error(HttpStatus.CONFLICT, "REFUND_AFTER_SETTLEMENT", "This booking is already included in a partner statement; a refund adjustment workflow is required");
        long available = availableRefund(bookingId, null);
        long policyCeiling = policyRefundCeiling(booking);
        long requestAmount = Math.min(available, policyCeiling);
        if (requestAmount <= 0) throw error(HttpStatus.CONFLICT, "NO_REFUNDABLE_PAYMENT", "This cancellation is not eligible for a refund under the tour's policy");
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setBookingId(bookingId); refundRequest.setRequestedBy(customerId);
        refundRequest.setReason(normalized.reason()); refundRequest.setRequestedAmountVnd(requestAmount);
        refundRequest.setIdempotencyKey(key); refundRequest.setStatus(RefundStatus.REQUESTED);
        refundRequest = requests.saveAndFlush(refundRequest);
        audit(customerId, "CUSTOMER_REFUND_REQUESTED", "refund_request", refundRequest.getId(), normalized.reason(), key,
                new Object[]{customerId, bookingId, normalized});
        return refundResponse(refundRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminRefundPage refundRequests(String rawStatus, int page, int size) {
        validatePage(page, size);
        RefundStatus status = null;
        if (rawStatus != null && !rawStatus.isBlank()) {
            try { status = RefundStatus.valueOf(rawStatus.trim().toUpperCase()); }
            catch (IllegalArgumentException ex) { throw error(HttpStatus.BAD_REQUEST, "REFUND_STATUS", "Unsupported refund status"); }
        }
        var result = status == null ? requests.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                : requests.findAllByStatus(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        var items = result.getContent().stream().map(r -> {
            var booking = r.getBooking(); var tour = booking.getSchedule().getTour();
            var history = refunds.findAllByRefundRequestIdOrderByRefundedAtAscIdAsc(r.getId()).stream()
                    .map(t -> new AdminRefundPage.Refund(t.getId(), t.getAmountVnd(), t.getChannel().getValue(),
                            t.getTransactionReference(), t.getRefundedAt(), t.getEvidenceUrl())).toList();
            return new AdminRefundPage.Item(r.getId(), booking.getId(), booking.getBookingCode(), booking.getCompanyId(),
                    tour.getCompany().getName(), r.getRequestedAmountVnd(), r.getApprovedAmountVnd(), r.getStatus().getValue(),
                    r.getReason(), r.getDecisionNote(), r.getCreatedAt(), refunds.refundedForRequest(r.getId()), history);
        }).toList();
        return new AdminRefundPage(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public RefundActionResponse decideRefund(long adminId, long refundRequestId, String key, RefundDecisionRequest request) {
        var normalized = new RefundDecisionRequest(request.decision().trim().toLowerCase(), request.approvedAmountVnd(), request.note().trim());
        var prior = idempotency.begin("ADMIN_REFUND_DECIDED", key, new Object[]{refundRequestId, normalized});
        if (prior.isPresent()) {
            return refundResponse(requests.findById(prior.get().getEntityId()).orElseThrow());
        }
        RefundRequest item = requests.lockById(refundRequestId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Refund request not found"));
        Booking booking = bookings.lockById(item.getBookingId()).orElseThrow();
        if (item.getStatus() != RefundStatus.REQUESTED)
            throw error(HttpStatus.CONFLICT, "REFUND_ALREADY_DECIDED", "Refund request is no longer awaiting a decision");
        if ("reject".equals(normalized.decision())) {
            item.setStatus(RefundStatus.REJECTED); item.setDecisionNote(normalized.note());
        } else if ("approve".equals(normalized.decision())) {
            lockStatementCompany(booking.getCompanyId());
            if (statementItems.existsByBookingIdAndKind(item.getBookingId(), ItemKind.SALE))
                throw error(HttpStatus.CONFLICT, "REFUND_AFTER_SETTLEMENT", "This booking is already included in a partner statement; an adjustment workflow is required before approving a refund");
            if (normalized.approvedAmountVnd() == null)
                throw error(HttpStatus.BAD_REQUEST, "APPROVED_AMOUNT_REQUIRED", "Approved amount is required");
            long available = availableRefund(item.getBookingId(), item.getId());
            if (normalized.approvedAmountVnd() > item.getRequestedAmountVnd() || normalized.approvedAmountVnd() > available)
                throw error(HttpStatus.CONFLICT, "REFUND_AMOUNT_LIMIT", "Approved amount exceeds available funds");
            item.setStatus(RefundStatus.APPROVED); item.setApprovedAmountVnd(normalized.approvedAmountVnd());
            item.setApprovedBy(adminId); item.setApprovedAt(OffsetDateTime.now()); item.setDecisionNote(normalized.note());
        } else throw error(HttpStatus.BAD_REQUEST, "REFUND_DECISION", "Decision must be approve or reject");
        requests.save(item);
        audit(adminId, "ADMIN_REFUND_DECIDED", "refund_request", item.getId(), normalized.note(), key,
                new Object[]{refundRequestId, normalized});
        return refundResponse(item);
    }

    @Override
    @Transactional
    public RefundActionResponse recordRefund(long adminId, long refundRequestId, String key, RecordRefundRequest request) {
        var normalized = new RecordRefundRequest(request.amountVnd(), request.channel(), request.transactionReference().trim(),
                request.refundedAt(), request.originalPaymentId(), EvidenceUrl.require(request.evidenceUrl()));
        var prior = idempotency.begin("ADMIN_REFUND_RECORDED", key, new Object[]{refundRequestId, normalized});
        if (prior.isPresent()) {
            CustomerRefund existing = refunds.findById(prior.get().getEntityId()).orElseThrow();
            return refundResponse(requests.findById(existing.getRefundRequestId()).orElseThrow());
        }
        RefundRequest item = requests.lockById(refundRequestId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Refund request not found"));
        bookings.lockById(item.getBookingId()).orElseThrow();
        if (item.getStatus() != RefundStatus.APPROVED && item.getStatus() != RefundStatus.PROCESSING)
            throw error(HttpStatus.CONFLICT, "REFUND_NOT_APPROVED", "Only an approved refund can be recorded");
        long remaining = item.getApprovedAmountVnd() - refunds.refundedForRequest(item.getId());
        if (normalized.amountVnd() > remaining)
            throw error(HttpStatus.CONFLICT, "REFUND_AMOUNT_LIMIT", "Refund exceeds the approved remaining amount");
        if (normalized.originalPaymentId() != null) {
            CustomerPayment payment = payments.findById(normalized.originalPaymentId()).orElseThrow(
                    () -> error(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found"));
            if (!payment.getBookingId().equals(item.getBookingId()))
                throw error(HttpStatus.BAD_REQUEST, "ORIGINAL_PAYMENT", "Original payment must belong to this booking");
        }
        CustomerRefund refund = new CustomerRefund();
        refund.setRefundRequestId(item.getId()); refund.setBookingId(item.getBookingId());
        refund.setOriginalPaymentId(normalized.originalPaymentId()); refund.setAmountVnd(normalized.amountVnd());
        refund.setChannel(normalized.channel()); refund.setTransactionReference(normalized.transactionReference());
        refund.setIdempotencyKey(key); refund.setRefundedAt(normalized.refundedAt()); refund.setRecordedBy(adminId);
        refund.setEvidenceUrl(normalized.evidenceUrl()); refunds.saveAndFlush(refund);
        long totalRefunded = refunds.refundedForRequest(item.getId());
        item.setStatus(totalRefunded >= item.getApprovedAmountVnd() ? RefundStatus.COMPLETED : RefundStatus.PROCESSING);
        requests.save(item);
        audit(adminId, "ADMIN_REFUND_RECORDED", "customer_refund", refund.getId(), "External refund evidence recorded", key,
                new Object[]{refundRequestId, normalized});
        return refundResponse(item);
    }

    private void acceptPayment(CustomerPayment payment, Booking booking, TourSchedule schedule, long adminId, String evidence) {
        payment.setAllocationStatus(AllocationStatus.ACCEPTED); payment.setVerifiedBy(adminId); payment.setEvidenceUrl(evidence);
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            schedule.setHeldSeats(schedule.getHeldSeats() - booking.getParticipantCount());
            schedule.setConfirmedSeats(schedule.getConfirmedSeats() + booking.getParticipantCount());
            booking.setStatus(BookingStatus.CONFIRMED); schedules.save(schedule); bookings.save(booking);
        }
        if (payment.getAttempt() != null) {
            payment.getAttempt().setStatus(PaymentStatus.SUCCEEDED); attempts.save(payment.getAttempt());
        }
    }

    private boolean canAccept(Booking booking, TourSchedule schedule, long amount, PaymentPurpose purpose) {
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED && schedule.getStatus() != ScheduleStatus.ONGOING) return false;
        long total = booking.getParticipantCount().longValue() * (booking.getBasePriceVnd() + booking.getEquipmentFeeVnd());
        long accepted = payments.acceptedTotal(booking.getId());
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            if (!booking.getHoldExpiresAt().isAfter(OffsetDateTime.now()) || schedule.getHeldSeats() < booking.getParticipantCount()) return false;
            if (purpose == null || (purpose != PaymentPurpose.DEPOSIT && purpose != PaymentPurpose.FULL)) return false;
            long deposit = BigDecimal.valueOf(total).multiply(booking.getDepositPercent())
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValue();
            long due = purpose == PaymentPurpose.FULL ? total - accepted : deposit - accepted;
            return due > 0 && amount == due;
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED) return amount == total - accepted && amount > 0;
        return false;
    }

    private long availableRefund(long bookingId, Long excludeRequestId) {
        return payments.acceptedTotal(bookingId) - refunds.refundedTotal(bookingId)
                - requests.reservedAmount(bookingId, excludeRequestId);
    }

    private long policyRefundCeiling(Booking booking) {
        long accepted = payments.acceptedTotal(booking.getId());
        if (booking.getCancelledBy() != null) return accepted;
        BigDecimal percent = new BigDecimal("100");
        try {
            JsonNode rules = mapper.readTree(booking.getPolicySnapshot()).path("refundRules");
            if (rules.isArray() && !rules.isEmpty()) {
                long daysLeft = Math.max(0, Duration.between(
                        booking.getCancelledAt() == null ? OffsetDateTime.now() : booking.getCancelledAt(),
                        booking.getSchedule().getStartsAt()).toDays());
                int threshold = -1;
                for (JsonNode rule : rules) {
                    int candidate = rule.path("daysBeforeDeparture").asInt(-1);
                    if (candidate >= 0 && candidate <= daysLeft && candidate > threshold) {
                        threshold = candidate;
                        percent = new BigDecimal(rule.path("refundPercent").asText("0"));
                    }
                }
            }
        } catch (Exception ignored) {
            // Existing bookings keep their legacy policy snapshot and admin review flow.
        }
        long totalRefundedAndReserved = refunds.refundedTotal(booking.getId())
                + requests.reservedAmount(booking.getId(), null);
        long policyAmount = BigDecimal.valueOf(accepted).multiply(percent)
                .divide(new BigDecimal("100"), 0, RoundingMode.DOWN).longValue();
        return Math.max(0, policyAmount - totalRefundedAndReserved);
    }

    private void lockStatementCompany(long companyId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", "statement-company:" + companyId).getSingleResult();
    }

    private PaymentVerificationResponse paymentResponse(CustomerPayment payment) {
        return new PaymentVerificationResponse(payment.getId(), payment.getAllocationStatus().getValue(),
                payment.getBooking().getStatus().getValue(), payments.acceptedTotal(payment.getBookingId()),
                payments.pendingReviewTotal(payment.getBookingId()));
    }

    private RefundActionResponse refundResponse(RefundRequest item) {
        return new RefundActionResponse(item.getId(), item.getStatus().getValue(), item.getRequestedAmountVnd(),
                item.getApprovedAmountVnd(), refunds.refundedForRequest(item.getId()));
    }

    private void audit(long actorId, String action, String entityType, long entityId, String reason,
            String key, Object request) {
        idempotency.record(actorId, action, entityType, entityId, reason, key, request);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw error(HttpStatus.BAD_REQUEST, "PAGINATION", "Invalid pagination");
    }
    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
