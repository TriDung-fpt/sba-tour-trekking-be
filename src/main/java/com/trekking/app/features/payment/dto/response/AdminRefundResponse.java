package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record AdminRefundResponse(Long id, Long refundRequestId, Long bookingId, Long originalPaymentId,
                                  Long amountVnd, String channel, String transactionReference,
                                  OffsetDateTime refundedAt, Long recordedBy) {
}
