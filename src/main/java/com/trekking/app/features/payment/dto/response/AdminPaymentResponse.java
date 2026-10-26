package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record AdminPaymentResponse(Long id, Long bookingId, Long attemptId, String provider,
                                   String transactionReference, Long amountVnd, Long gatewayFeeVnd,
                                   String allocationStatus, OffsetDateTime receivedAt, Long verifiedBy) {
}
