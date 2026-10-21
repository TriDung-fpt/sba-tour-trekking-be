package com.trekking.app.features.payment.dto.response;

public record RefundActionResponse(long refundRequestId, String status, long requestedAmountVnd,
        Long approvedAmountVnd, long refundedVnd) {}
