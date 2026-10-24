package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.account.service.BusinessAuthorizationService;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.request.PayOsWebhookRequest;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.entity.CustomerPayment;
import com.trekking.app.features.payment.entity.PaymentAttempt;
import com.trekking.app.features.payment.entity.PaymentNotice;
import com.trekking.app.features.payment.enums.AllocationStatus;
import com.trekking.app.features.payment.enums.GatewayProvider;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentStatus;
import com.trekking.app.features.payment.repository.CustomerPaymentRepository;
import com.trekking.app.features.payment.repository.PaymentAttemptRepository;
import com.trekking.app.features.payment.repository.PaymentNoticeRepository;
import com.trekking.app.features.payment.service.PaymentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaymentAttemptRepository attempts;
    private final CustomerPaymentRepository payments;
    private final PaymentNoticeRepository notices;
    private final BusinessAuthorizationService businessAuthorization;
    private final EntityManager entities;
    private final PayOsGateway gateway;
    private final ObjectMapper json;
    private final Clock clock;

    public PaymentServiceImpl(PaymentAttemptRepository attempts, CustomerPaymentRepository payments,
                              PaymentNoticeRepository notices, BusinessAuthorizationService businessAuthorization,
                              EntityManager entities, PayOsGateway gateway,
                              ObjectMapper json, Clock clock) {
        this.attempts = attempts;
        this.payments = payments;
        this.notices = notices;
        this.businessAuthorization = businessAuthorization;
        this.entities = entities;
        this.gateway = gateway;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentLinkResponse createPayOsLink(long bookingId, CreatePaymentLinkRequest request) {
        businessAuthorization.requirePayment(bookingId);
        Booking booking = entities.find(Booking.class, bookingId);
        if (booking == null) throw notFound();

        BigDecimal paid = payments.acceptedTotal(bookingId);
        var existing = attempts.findFirstByBookingIdAndPurposeAndStatusOrderByCreatedAtDesc(
                bookingId, request.purpose(), PaymentStatus.PENDING);
        if (existing.isPresent() && existing.get().getExpiresAt() != null
                && existing.get().getExpiresAt().isAfter(OffsetDateTime.now(clock))
                && existing.get().getCheckoutUrl() != null) {
            var active = existing.get();
            return new PaymentLinkResponse(active.getId(), active.getCheckoutUrl(), active.getMerchantReference(),
                    active.getRequestedAmountVnd(), active.getStatus().getValue(), active.getExpiresAt());
        }
        long amount = amountFor(booking, paid, request.purpose());
        if (amount > 2_000_000_000L) {
            throw conflict("PAYMENT_AMOUNT_UNSUPPORTED", "Payment amount exceeds the PayOS single-payment limit");
        }
        OffsetDateTime expiresAt = booking.getStatus() == BookingStatus.PENDING_PAYMENT
                ? booking.getHoldExpiresAt() : booking.getBalanceDueAt();
        long expiryEpoch = expiresAt.toEpochSecond();
        if (expiryEpoch > Integer.MAX_VALUE) throw conflict("PAYMENT_EXPIRY_UNSUPPORTED", "Payment expiry is outside PayOS supported range");

        long orderCode = ThreadLocalRandom.current().nextLong(100_000_000L, 2_000_000_000L);
        String reference = Long.toString(orderCode);
        String description = "TRK" + orderCode;
        var link = gateway.createLink(orderCode, amount, description, expiryEpoch);

        var attempt = new PaymentAttempt();
        attempt.setBookingId(bookingId);
        attempt.setProvider(PaymentProvider.PAYOS);
        attempt.setMerchantReference(reference);
        attempt.setPurpose(request.purpose());
        attempt.setRequestedAmountVnd(amount);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setExpiresAt(expiresAt);
        attempt.setCheckoutUrl(link.checkoutUrl());
        attempt = attempts.save(attempt);
        return new PaymentLinkResponse(attempt.getId(), link.checkoutUrl(), reference, amount,
                attempt.getStatus().getValue(), expiresAt);
    }

    @Override
    @Transactional
    public void processPayOsWebhook(PayOsWebhookRequest request) {
        JsonNode data = request.data();
        if (data == null || data.isNull()) throw badRequest("PAYOS_WEBHOOK_INVALID", "Missing PayOS payment data");
        String canonicalData = data.properties().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + scalarValue(entry.getValue()))
                .collect(Collectors.joining("&"));
        if (!gateway.verifyWebhook(request.signature(), canonicalData)) {
            throw badRequest("PAYOS_SIGNATURE_INVALID", "PayOS webhook signature is invalid");
        }

        String reference = text(data, "reference");
        String eventKey = reference == null || reference.isBlank() ? text(data, "orderCode") : reference;
        if (eventKey == null || eventKey.isBlank()) throw badRequest("PAYOS_WEBHOOK_INVALID", "Missing PayOS transaction reference");
        if (notices.existsByProviderAndEventKey(GatewayProvider.PAYOS, eventKey)) return;

        long orderCode = number(data, "orderCode");
        var initialAttempt = attempts.findByMerchantReference(Long.toString(orderCode))
                .orElseThrow(() -> notFound());
        Booking booking = entities.find(Booking.class, initialAttempt.getBookingId(), LockModeType.PESSIMISTIC_WRITE);
        if (booking == null) throw notFound();
        var attempt = attempts.findByMerchantReferenceForUpdate(Long.toString(orderCode))
                .orElseThrow(() -> notFound());
        var notice = new PaymentNotice();
        notice.setAttemptId(attempt.getId());
        notice.setProvider(GatewayProvider.PAYOS);
        notice.setEventKey(eventKey);
        notice.setSignatureValid(true);
        notice.setPayload(safePayload(request, data));

        if (!request.success() || !"00".equals(text(data, "code"))) {
            if (attempt.getStatus() == PaymentStatus.PENDING) attempt.setStatus(PaymentStatus.FAILED);
            notice.setProcessingError("PayOS reported an unsuccessful payment");
            notice.setProcessedAt(OffsetDateTime.now(clock));
            attempts.save(attempt);
            notices.save(notice);
            return;
        }

        long amount = number(data, "amount");
        if (amount != attempt.getRequestedAmountVnd()) {
            notice.setProcessingError("PayOS amount did not match the payment attempt");
            notice.setProcessedAt(OffsetDateTime.now(clock));
            notices.save(notice);
            return;
        }
        if (attempt.getStatus() == PaymentStatus.SUCCEEDED) {
            notice.setProcessedAt(OffsetDateTime.now(clock));
            notices.save(notice);
            return;
        }
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            notice.setProcessingError("PayOS payment arrived after the attempt was no longer pending");
            notice.setProcessedAt(OffsetDateTime.now(clock));
            notices.save(notice);
            return;
        }

        BigDecimal acceptedTotal = payments.acceptedTotal(attempt.getBookingId());
        boolean late = attempt.getExpiresAt() == null || !attempt.getExpiresAt().isAfter(OffsetDateTime.now(clock))
                || booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.CANCELLED;
        boolean exceedsOutstanding = acceptedTotal.add(BigDecimal.valueOf(amount))
                .compareTo(BigDecimal.valueOf(booking.getTotalPriceVnd())) > 0;

        var payment = new CustomerPayment();
        payment.setBookingId(attempt.getBookingId());
        payment.setAttemptId(attempt.getId());
        payment.setProvider(PaymentProvider.PAYOS);
        payment.setTransactionReference(reference);
        payment.setAmountVnd(amount);
        payment.setGatewayFeeVnd(0L);
        payment.setAllocationStatus(late || exceedsOutstanding ? AllocationStatus.PENDING_REVIEW : AllocationStatus.ACCEPTED);
        payment.setReceivedAt(OffsetDateTime.now(clock));
        payments.save(payment);
        attempt.setStatus(PaymentStatus.SUCCEEDED);
        attempts.save(attempt);
        if (late) notice.setProcessingError("Payment arrived after the attempt or booking expired; manual review required");
        else if (exceedsOutstanding) notice.setProcessingError("Payment exceeds the remaining booking amount; manual review required");
        notice.setProcessedAt(OffsetDateTime.now(clock));
        notices.save(notice);
    }

    private long amountFor(Booking booking, BigDecimal paid, PaymentPurpose purpose) {
        long total = booking.getTotalPriceVnd();
        long received = paid.longValueExact();
        if (received >= total) throw conflict("BOOKING_ALREADY_PAID", "Booking has no remaining amount due");
        return switch (purpose) {
            case DEPOSIT -> {
                if (received > 0) throw conflict("DEPOSIT_ALREADY_PAID", "Deposit has already been received");
                yield BigDecimal.valueOf(total).multiply(booking.getDepositPercent())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
            }
            case BALANCE -> {
                if (received == 0) throw conflict("DEPOSIT_REQUIRED", "A deposit must be paid before the balance");
                yield total - received;
            }
            case FULL -> total - received;
        };
    }

    private String safePayload(PayOsWebhookRequest request, JsonNode data) {
        try {
            return json.writeValueAsString(Map.of("code", request.code() == null ? "" : request.code(),
                    "success", request.success(), "data", Map.of("orderCode", safeText(data, "orderCode"),
                            "amount", safeText(data, "amount"), "reference", safeText(data, "reference"),
                            "code", safeText(data, "code"))));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String safeText(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? "" : value;
    }

    private static String scalarValue(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isTextual()) return value.stringValue();
        if (value.isValueNode()) return value.toString();
        return value.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong()) throw badRequest("PAYOS_WEBHOOK_INVALID", "Invalid PayOS " + field);
        return value.longValue();
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

    private static AuthException badRequest(String code, String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, code, message);
    }
}
