package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.response.BookingPaymentStatus;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import vn.payos.model.webhooks.Webhook;

public interface PaymentService {
    PaymentLinkResponse createLink(long accountId, long bookingId, CreatePaymentLinkRequest request);
    BookingPaymentStatus status(long accountId, long bookingId);
    void handlePayOSWebhook(Webhook webhook);
    boolean enabled();
}
