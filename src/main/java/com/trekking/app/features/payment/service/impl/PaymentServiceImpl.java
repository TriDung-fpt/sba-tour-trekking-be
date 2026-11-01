package com.trekking.app.features.payment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.account.service.BusinessAuthorizationService;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.service.BookingPaymentService;
import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
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
import com.trekking.app.features.payment.service.PaymentGateway;
import com.trekking.app.features.equipment.service.EquipmentReservationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaymentAttemptRepository attempts;
    private final CustomerPaymentRepository payments;
    private final PaymentNoticeRepository notices;
    private final BusinessAuthorizationService businessAuthorization;
    private final EntityManager entities;
    private final EnumMap<PaymentProvider, PaymentGateway> gateways;
    private final EquipmentReservationService equipmentReservations;
    private final BookingPaymentService bookingPayments;
    private final Clock clock;

    public PaymentServiceImpl(PaymentAttemptRepository attempts, CustomerPaymentRepository payments,
                              PaymentNoticeRepository notices, BusinessAuthorizationService businessAuthorization,
                              EntityManager entities, List<PaymentGateway> gateways, EquipmentReservationService equipmentReservations,
                              BookingPaymentService bookingPayments, Clock clock) {
        this.attempts = attempts;
        this.payments = payments;
        this.notices = notices;
        this.businessAuthorization = businessAuthorization;
        this.entities = entities;
        this.gateways = new EnumMap<>(PaymentProvider.class);
        gateways.forEach(gateway -> this.gateways.put(gateway.provider(), gateway));
        this.equipmentReservations = equipmentReservations;
        this.bookingPayments = bookingPayments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentLinkResponse createPaymentLink(long bookingId, CreatePaymentLinkRequest request) {
        PaymentGateway gateway = gateway(request.provider());
        businessAuthorization.requirePayment(bookingId);
        Booking booking = entities.find(Booking.class, bookingId);
        if (booking == null) throw notFound();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (booking.getStatus() == BookingStatus.PENDING_PAYMENT && !booking.getHoldExpiresAt().isAfter(now)) {
            throw conflict("BOOKING_HOLD_EXPIRED", "Booking hold has expired");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw conflict("BOOKING_NOT_PAYABLE", "Booking is not in a payable state");
        }
        if (booking.getStatus() == BookingStatus.CONFIRMED && request.purpose() == PaymentPurpose.DEPOSIT) {
            throw conflict("DEPOSIT_ALREADY_PAID", "A confirmed booking cannot accept a deposit payment");
        }

        BigDecimal paid = payments.acceptedTotal(bookingId);
        var existing = attempts.findFirstByBookingIdAndProviderAndPurposeAndStatusOrderByCreatedAtDesc(
                bookingId, request.provider(), request.purpose(), PaymentStatus.PENDING);
        if (existing.isPresent() && existing.get().getExpiresAt() != null
                && existing.get().getExpiresAt().isAfter(OffsetDateTime.now(clock))
                && existing.get().getCheckoutUrl() != null) {
            var active = existing.get();
            return new PaymentLinkResponse(active.getId(), active.getProvider(), active.getCheckoutUrl(), active.getMerchantReference(),
                    active.getRequestedAmountVnd(), active.getStatus().getValue(), active.getExpiresAt());
        }
        long amount = amountFor(booking, paid, request.purpose());
        OffsetDateTime expiresAt = booking.getStatus() == BookingStatus.PENDING_PAYMENT
                ? booking.getHoldExpiresAt() : booking.getBalanceDueAt();

        long orderCode = ThreadLocalRandom.current().nextLong(100_000_000L, 2_000_000_000L);
        String reference = Long.toString(orderCode);
        String description = "TRK" + orderCode;
        String checkoutUrl = gateway.createLink(reference, amount, description, expiresAt);

        var attempt = new PaymentAttempt();
        attempt.setBookingId(bookingId);
        attempt.setProvider(request.provider());
        attempt.setMerchantReference(reference);
        attempt.setPurpose(request.purpose());
        attempt.setRequestedAmountVnd(amount);
        attempt.setStatus(PaymentStatus.PENDING);
        attempt.setExpiresAt(expiresAt);
        attempt.setCheckoutUrl(checkoutUrl);
        attempt = attempts.save(attempt);
        return new PaymentLinkResponse(attempt.getId(), attempt.getProvider(), checkoutUrl, reference, amount,
                attempt.getStatus().getValue(), expiresAt);
    }

    @Override
    @Transactional
    public void processWebhook(PaymentProvider provider, JsonNode request) {
        PaymentGateway gateway = gateway(provider);
        PaymentGateway.WebhookEvent event = gateway.verifyAndParseWebhook(request);
        // PayOS sends this signed sample when confirming the channel webhook URL.
        if (provider == PaymentProvider.PAYOS
                && "00".equals(request.path("data").path("code").asString())
                && "123".equals(event.merchantReference()) && event.amountVnd() == 3000L
                && "TF230204212323".equals(event.eventKey())) {
            return;
        }
        GatewayProvider noticeProvider = GatewayProvider.valueOf(provider.name());
        if (notices.existsByProviderAndEventKey(noticeProvider, event.eventKey())) return;

        var initialAttempt = attempts.findByMerchantReference(event.merchantReference())
                .orElseThrow(() -> notFound());
        if (initialAttempt.getProvider() != provider) throw notFound();
        Booking booking = entities.find(Booking.class, initialAttempt.getBookingId(), LockModeType.PESSIMISTIC_WRITE);
        if (booking == null) throw notFound();
        var attempt = attempts.findByMerchantReferenceForUpdate(event.merchantReference())
                .orElseThrow(() -> notFound());
        if (attempt.getProvider() != provider) throw notFound();
        var notice = new PaymentNotice();
        notice.setAttemptId(attempt.getId());
        notice.setProvider(noticeProvider);
        notice.setEventKey(event.eventKey());
        notice.setSignatureValid(true);
        notice.setPayload(event.safePayload());

        if (!event.successful()) {
            if (attempt.getStatus() == PaymentStatus.PENDING) attempt.setStatus(PaymentStatus.FAILED);
            notice.setProcessingError("Payment provider reported an unsuccessful payment");
            notice.setProcessedAt(OffsetDateTime.now(clock));
            attempts.save(attempt);
            notices.save(notice);
            return;
        }

        long amount = event.amountVnd();
        if (amount != attempt.getRequestedAmountVnd()) {
            notice.setProcessingError("Payment amount did not match the payment attempt");
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
            notice.setProcessingError("Payment arrived after the attempt was no longer pending");
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
        payment.setProvider(provider);
        payment.setTransactionReference(event.eventKey());
        payment.setAmountVnd(amount);
        payment.setGatewayFeeVnd(0L);
        payment.setAllocationStatus(late || exceedsOutstanding ? AllocationStatus.PENDING_REVIEW : AllocationStatus.ACCEPTED);
        payment.setReceivedAt(OffsetDateTime.now(clock));
        payments.save(payment);
        attempt.setStatus(PaymentStatus.SUCCEEDED);
        attempts.save(attempt);
        if (!late && !exceedsOutstanding) {
            if (bookingPayments.confirmWhenDepositPaid(attempt.getBookingId())) {
                equipmentReservations.confirm(attempt.getBookingId());
            }
        }
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
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).longValueExact();
            }
            case BALANCE -> {
                if (received == 0) throw conflict("DEPOSIT_REQUIRED", "A deposit must be paid before the balance");
                yield total - received;
            }
            case FULL -> total - received;
        };
    }

    private PaymentGateway gateway(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_PROVIDER_UNAVAILABLE", "Selected payment provider is not configured");
        return gateway;
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }

}
