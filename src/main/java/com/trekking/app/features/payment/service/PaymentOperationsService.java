package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.CreateRefundRequest;
import com.trekking.app.features.payment.dto.request.ManualPaymentRequest;
import com.trekking.app.features.payment.dto.request.RecordRefundRequest;
import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.request.VerifyPaymentRequest;
import com.trekking.app.features.payment.dto.response.AdminPaymentPage;
import com.trekking.app.features.payment.dto.response.AdminRefundPage;
import com.trekking.app.features.payment.dto.response.PaymentVerificationResponse;
import com.trekking.app.features.payment.dto.response.RefundActionResponse;

public interface PaymentOperationsService {
    AdminPaymentPage pendingReview(int page, int size);
    PaymentVerificationResponse verify(long adminId, long paymentId, String idempotencyKey, VerifyPaymentRequest request);
    PaymentVerificationResponse manualPayment(long adminId, long bookingId, String idempotencyKey, ManualPaymentRequest request);
    RefundActionResponse requestRefund(long customerId, long bookingId, String idempotencyKey, CreateRefundRequest request);
    AdminRefundPage refundRequests(String status, int page, int size);
    RefundActionResponse decideRefund(long adminId, long refundRequestId, String idempotencyKey, RefundDecisionRequest request);
    RefundActionResponse recordRefund(long adminId, long refundRequestId, String idempotencyKey, RecordRefundRequest request);
}
