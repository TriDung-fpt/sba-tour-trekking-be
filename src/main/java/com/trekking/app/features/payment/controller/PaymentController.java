package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.enums.PaymentProvider;
import com.trekking.app.features.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/bookings/{bookingId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse createPaymentLink(@PathVariable long bookingId,
                                                 @Valid @RequestBody CreatePaymentLinkRequest request) {
        return payments.createPaymentLink(bookingId, request);
    }

    @PostMapping("/payments/{provider}/webhook")
    public WebhookAcknowledgement webhook(@PathVariable String provider, @RequestBody tools.jackson.databind.JsonNode request) {
        PaymentProvider selectedProvider;
        try {
            selectedProvider = PaymentProvider.valueOf(provider.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment provider");
        }
        payments.processWebhook(selectedProvider, request);
        return new WebhookAcknowledgement("00", "success", true);
    }

    public record WebhookAcknowledgement(String code, String message, boolean acknowledged) {}
}
