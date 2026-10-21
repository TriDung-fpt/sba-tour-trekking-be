package com.trekking.app.features.payment.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.payment.dto.request.ManualPaymentRequest;
import com.trekking.app.features.payment.dto.request.RecordRefundRequest;
import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.request.VerifyPaymentRequest;
import com.trekking.app.features.payment.dto.response.AdminPaymentPage;
import com.trekking.app.features.payment.dto.response.AdminRefundPage;
import com.trekking.app.features.payment.dto.response.PaymentVerificationResponse;
import com.trekking.app.features.payment.dto.response.RefundActionResponse;
import com.trekking.app.features.payment.service.PaymentOperationsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinancePaymentController {
    private final PaymentOperationsService operations;

    public AdminFinancePaymentController(PaymentOperationsService operations) { this.operations = operations; }

    @GetMapping("/payments/pending-review")
    public AdminPaymentPage pendingPayments(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return operations.pendingReview(page, size);
    }

    @PostMapping("/payments/{paymentId}/verify")
    public PaymentVerificationResponse verifyPayment(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long paymentId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody VerifyPaymentRequest request) {
        return operations.verify(admin.accountId(), paymentId, key, request);
    }

    @PostMapping("/bookings/{bookingId}/manual-payments")
    public PaymentVerificationResponse manualPayment(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long bookingId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ManualPaymentRequest request) {
        return operations.manualPayment(admin.accountId(), bookingId, key, request);
    }

    @GetMapping("/refund-requests")
    public AdminRefundPage refundRequests(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return operations.refundRequests(status, page, size);
    }

    @PostMapping("/refund-requests/{refundRequestId}/decision")
    public RefundActionResponse decideRefund(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long refundRequestId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RefundDecisionRequest request) {
        return operations.decideRefund(admin.accountId(), refundRequestId, key, request);
    }

    @PostMapping("/refund-requests/{refundRequestId}/record-refund")
    public RefundActionResponse recordRefund(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long refundRequestId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RecordRefundRequest request) {
        return operations.recordRefund(admin.accountId(), refundRequestId, key, request);
    }
}
