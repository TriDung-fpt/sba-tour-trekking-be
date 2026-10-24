package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.request.PayOsWebhookRequest;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;

public interface PaymentService {
    PaymentLinkResponse createPayOsLink(long bookingId, CreatePaymentLinkRequest request);
    void processPayOsWebhook(PayOsWebhookRequest request);
}
