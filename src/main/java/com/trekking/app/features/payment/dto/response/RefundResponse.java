package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record RefundResponse(long id, long bookingId, long requestedAmountVnd, Long approvedAmountVnd,
                             String status, String recipientAccountLast4, String recipientVerification,
                             OffsetDateTime createdAt, String payoutReferenceId) {}
