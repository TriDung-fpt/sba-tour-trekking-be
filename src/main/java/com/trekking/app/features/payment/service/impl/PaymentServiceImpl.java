package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.config.PayOSProperties;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.response.BookingPaymentStatus;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.PaymentAttempt;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.GatewayProvider;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.CustomerRefundRepository;
import com.trekking.app.features.payment.repository.PaymentAttemptRepository;
import com.trekking.app.features.payment.repository.PaymentNoticeRepository;
import com.trekking.app.features.payment.service.PaymentService;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final BookingRepository bookings;
    private final TourScheduleRepository schedules;
    private final PaymentAttemptRepository attempts;
    private final PaymentNoticeRepository notices;
    private final CustomerPaymentRepository payments;
    private final CustomerRefundRepository refunds;
    private final AuditLogRepository audits;
    private final ObjectProvider<PayOS> payOsProvider;
    private final PayOSProperties properties;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public PaymentServiceImpl(BookingRepository bookings, TourScheduleRepository schedules,
            PaymentAttemptRepository attempts, PaymentNoticeRepository notices,
            CustomerPaymentRepository payments, CustomerRefundRepository refunds,
            AuditLogRepository audits, ObjectProvider<PayOS> payOsProvider,
            PayOSProperties properties, ObjectMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.bookings = bookings; this.schedules = schedules; this.attempts = attempts; this.notices = notices;
        this.payments = payments; this.refunds = refunds; this.audits = audits;
        this.payOsProvider = payOsProvider; this.properties = properties; this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override public boolean enabled() { return properties.configured() && payOsProvider.getIfAvailable() != null; }

    @Override
    public PaymentLinkResponse createLink(long accountId, long bookingId, CreatePaymentLinkRequest request) {
        PayOS client = configuredClient();
        PreparedPayment prepared = transactions.execute(status -> {
        Booking booking = bookings.lockCustomerBooking(bookingId, accountId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        OffsetDateTime now = OffsetDateTime.now();
        long total = total(booking);
        long accepted = payments.acceptedTotal(bookingId);
        long amount;
        OffsetDateTime expires;
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
            if (!booking.getHoldExpiresAt().isAfter(now)) throw error(HttpStatus.CONFLICT, "HOLD_EXPIRED", "Payment hold has expired");
            if (request.purpose() != PaymentPurpose.DEPOSIT && request.purpose() != PaymentPurpose.FULL)
                throw error(HttpStatus.BAD_REQUEST, "PAYMENT_PURPOSE", "Choose deposit or full payment for this booking");
            long deposit = depositDue(booking, total);
            amount = request.purpose() == PaymentPurpose.FULL ? total - accepted : deposit - accepted;
            expires = booking.getHoldExpiresAt();
        } else if (booking.getStatus() == BookingStatus.CONFIRMED && request.purpose() == PaymentPurpose.BALANCE) {
            amount = total - accepted;
            expires = now.plusMinutes(30);
        } else {
            throw error(HttpStatus.CONFLICT, "BOOKING_STATE", "This booking is not accepting this payment");
        }
        if (amount <= 0) throw error(HttpStatus.CONFLICT, "NO_BALANCE_DUE", "There is no amount due for this payment");
        if (attempts.existsByBookingIdAndPurposeAndStatusInAndExpiresAtAfter(
                bookingId, request.purpose(), List.of(PaymentStatus.CREATED, PaymentStatus.PENDING), now))
            throw error(HttpStatus.CONFLICT, "PAYMENT_ALREADY_PENDING", "A payment link is already active for this booking");

        long orderCode = ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999_999L);
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setBookingId(bookingId); attempt.setProvider(PaymentProvider.PAYOS);
        attempt.setMerchantReference(Long.toString(orderCode)); attempt.setPurpose(request.purpose());
        attempt.setRequestedAmountVnd(amount); attempt.setStatus(PaymentStatus.CREATED); attempt.setExpiresAt(expires);
        attempt = attempts.saveAndFlush(attempt);
        return new PreparedPayment(attempt.getId(), orderCode, request.purpose().getValue(), amount, expires);
        });

        var body = vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest.builder()
                .orderCode(prepared.orderCode()).amount(prepared.amount()).description("WT" + Long.toString(bookingId, 36).toUpperCase())
                .returnUrl(resultUrl(properties.returnUrl(), bookingId, "success"))
                .cancelUrl(resultUrl(properties.cancelUrl(), bookingId, "cancelled"))
                .expiredAt(prepared.expiresAt().toEpochSecond()).build();
        try {
            var link = client.paymentRequests().create(body);
            transactions.executeWithoutResult(status -> attempts.findById(prepared.attemptId()).ifPresent(attempt -> {
                attempt.setStatus(PaymentStatus.PENDING); attempts.save(attempt);
            }));
            return new PaymentLinkResponse(true, prepared.attemptId(), request.purpose().getValue(), prepared.amount(),
                    "pending", link.getCheckoutUrl(), link.getQrCode(), prepared.expiresAt());
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(status -> attempts.findById(prepared.attemptId()).ifPresent(attempt -> {
                attempt.setStatus(PaymentStatus.FAILED); attempts.save(attempt);
            }));
            return new PaymentLinkResponse(true, prepared.attemptId(), request.purpose().getValue(), prepared.amount(),
                    "failed", null, null, prepared.expiresAt());
        }
    }

    private record PreparedPayment(long attemptId, long orderCode, String purpose, long amount, OffsetDateTime expiresAt) {}

    private String resultUrl(String base, long bookingId, String result) {
        return UriComponentsBuilder.fromUriString(base).queryParam("paymentReturn", result)
                .queryParam("bookingId", bookingId).build().encode().toUriString();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPaymentStatus status(long accountId, long bookingId) {
        Booking b = bookings.findByIdAndCustomerId(bookingId, accountId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        long total = total(b), accepted = payments.acceptedTotal(bookingId);
        long due = b.getStatus() == BookingStatus.PENDING_PAYMENT ? Math.max(0, depositDue(b, total) - accepted)
                : b.getStatus() == BookingStatus.CONFIRMED ? Math.max(0, total - accepted) : 0;
        var history = attempts.findAllByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(a -> new BookingPaymentStatus.Attempt(a.getId(), a.getPurpose().getValue(),
                        a.getRequestedAmountVnd(), a.getStatus().getValue(), a.getExpiresAt())).toList();
        return new BookingPaymentStatus(enabled(), b.getStatus().getValue(), total,
                payments.acceptedTotal(bookingId), payments.pendingReviewTotal(bookingId),
                refunds.refundedTotal(bookingId), due, history);
    }

    @Override
    @Transactional
    public void handlePayOSWebhook(Webhook webhook) {
        PayOS client = configuredClient();
        WebhookData data;
        try { data = client.webhooks().verify(webhook); }
        catch (RuntimeException ex) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PAYOS_SIGNATURE", "Webhook signature is invalid");
        }
        if (data == null || data.getOrderCode() == null) return;
        String reference = data.getReference();
        String eventKey = reference == null || reference.isBlank()
                ? data.getOrderCode() + ":" + data.getAmount() + ":" + data.getPaymentLinkId() : reference;
        var attempt = attempts.findByMerchantReference(data.getOrderCode().toString()).orElse(null);
        String payload = json(Map.of("orderCode", data.getOrderCode(), "amount", data.getAmount() == null ? 0L : data.getAmount(),
                "reference", reference == null ? "" : reference,
                "transactionDateTime", data.getTransactionDateTime() == null ? "" : data.getTransactionDateTime(),
                "code", data.getCode() == null ? "" : data.getCode()));
        if (notices.insertIfAbsent(attempt == null ? null : attempt.getId(), eventKey, payload, OffsetDateTime.now()) == 0) return;
        if (attempt == null || !Boolean.TRUE.equals(webhook.getSuccess()) || !"00".equals(data.getCode())) return;
        if (data.getAmount() == null || data.getAmount() <= 0) return;
        if (payments.findByAttemptId(attempt.getId()).isPresent()) return;

        Booking booking = bookings.lockById(attempt.getBookingId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found"));
        long amount = data.getAmount() == null ? 0 : data.getAmount();
        boolean exactAmount = amount == attempt.getRequestedAmountVnd();
        boolean eligible = (booking.getStatus() == BookingStatus.PENDING_PAYMENT
                && booking.getHoldExpiresAt().isAfter(OffsetDateTime.now())) || booking.getStatus() == BookingStatus.CONFIRMED;
        CustomerPayment payment = new CustomerPayment();
        payment.setBookingId(booking.getId()); payment.setAttemptId(attempt.getId());
        payment.setProvider(PaymentProvider.PAYOS); payment.setTransactionReference(requiredReference(reference, eventKey));
        payment.setAmountVnd(amount); payment.setGatewayFeeVnd(0L);
        payment.setAllocationStatus(exactAmount && eligible ? AllocationStatus.ACCEPTED : AllocationStatus.PENDING_REVIEW);
        payment.setReceivedAt(parseReceivedAt(data.getTransactionDateTime()));
        payments.saveAndFlush(payment);
        attempt.setStatus(PaymentStatus.SUCCEEDED); attempts.save(attempt);

        if (exactAmount && booking.getStatus() == BookingStatus.PENDING_PAYMENT
                && (attempt.getPurpose() == PaymentPurpose.DEPOSIT || attempt.getPurpose() == PaymentPurpose.FULL)) {
            var schedule = schedules.findByIdForBooking(booking.getScheduleId())
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Departure not found"));
            if (schedule.getStatus() == ScheduleStatus.SCHEDULED
                    && schedule.getHeldSeats() >= booking.getParticipantCount()) {
                schedule.setHeldSeats(schedule.getHeldSeats() - booking.getParticipantCount());
                schedule.setConfirmedSeats(schedule.getConfirmedSeats() + booking.getParticipantCount());
                booking.setStatus(BookingStatus.CONFIRMED);
                schedules.save(schedule); bookings.save(booking);
            }
        }
        audit(booking.getCustomerId(), "PAYMENT_RECEIVED_" + (exactAmount && eligible ? "ACCEPTED" : "REVIEW"),
                "booking", booking.getId(), null);
    }

    private PayOS configuredClient() {
        PayOS client = properties.configured() ? payOsProvider.getIfAvailable() : null;
        if (client == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                "Online payment is not available yet");
        return client;
    }
    private long total(Booking b) { return b.getParticipantCount().longValue() * (b.getBasePriceVnd() + b.getEquipmentFeeVnd()); }
    private long depositDue(Booking b, long total) {
        return BigDecimal.valueOf(total).multiply(b.getDepositPercent()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValue();
    }
    private String requiredReference(String reference, String fallback) {
        String value = reference == null || reference.isBlank() ? fallback : reference;
        return value.length() <= 200 ? value : value.substring(0, 200);
    }
    private OffsetDateTime parseReceivedAt(String value) {
        if (value == null || value.isBlank()) return OffsetDateTime.now();
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atOffset(ZoneOffset.ofHours(7)); }
        catch (RuntimeException ex) { return OffsetDateTime.now(); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize payment notice", ex); }
    }
    private void audit(long actorId, String action, String entityType, long entityId, String reason) {
        AuditLog log = new AuditLog(); log.setActorId(actorId); log.setAction(action); log.setEntityType(entityType);
        log.setEntityId(entityId); log.setReason(reason); log.setDetails("{}"); log.setOccurredAt(OffsetDateTime.now()); audits.save(log);
    }
    private ResponseStatusException error(HttpStatus status, String code, String message) {
        return new ResponseStatusException(status, code + ": " + message);
    }
}
