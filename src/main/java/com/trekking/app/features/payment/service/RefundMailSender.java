package com.trekking.app.features.payment.service;

/** Sends refund status emails to the customer. Never include full bank details in a notice. */
public interface RefundMailSender {
    void send(RefundNotice notice);

    record RefundNotice(String email, String customerName, String bookingCode, String status,
                        long amountVnd, String accountNumberMasked, String note) {}
}
