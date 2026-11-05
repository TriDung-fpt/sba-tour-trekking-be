package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.enums.PaymentProvider;
import tools.jackson.databind.JsonNode;

public interface PaymentService {
    PaymentLinkResponse createPaymentLink(long bookingId, CreatePaymentLinkRequest request);
    void processWebhook(PaymentProvider provider, JsonNode request);
}
