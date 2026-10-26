package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record AdminPaymentAttemptResponse(Long id, Long bookingId, String provider, String merchantReference,
                                          String purpose, Long requestedAmountVnd, String status,
                                          OffsetDateTime expiresAt, OffsetDateTime createdAt) {
}
