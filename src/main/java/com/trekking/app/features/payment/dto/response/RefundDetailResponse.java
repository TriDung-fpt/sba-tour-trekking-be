package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

public record RefundDetailResponse(long id, long bookingId, long requestedAmountVnd, Long approvedAmountVnd,
                                   String status, String reason, String bankBin, String accountHolderName,
                                   String accountNumber, String recipientVerification, OffsetDateTime createdAt,
                                   String payoutReferenceId, String payoutId, String payoutTransactionReference) {}
