package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.request.PayOsWebhookRequest;
import com.trekking.app.features.payment.dto.response.PayOsWebhookResponse;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/bookings/{bookingId}/payments/payos")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse createPayOsLink(@PathVariable long bookingId,
                                               @Valid @RequestBody CreatePaymentLinkRequest request) {
        return payments.createPayOsLink(bookingId, request);
    }

    @PostMapping("/payments/payos/webhook")
    public PayOsWebhookResponse payOsWebhook(@RequestBody PayOsWebhookRequest request) {
        payments.processPayOsWebhook(request);
        return new PayOsWebhookResponse("00", "success", true);
    }
}
