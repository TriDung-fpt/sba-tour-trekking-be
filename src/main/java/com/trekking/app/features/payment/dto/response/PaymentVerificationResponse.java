package com.trekking.app.features.payment.dto.response;

public record PaymentVerificationResponse(long paymentId, String allocationStatus, String bookingStatus,
        long receivedVnd, long pendingReviewVnd) {}
