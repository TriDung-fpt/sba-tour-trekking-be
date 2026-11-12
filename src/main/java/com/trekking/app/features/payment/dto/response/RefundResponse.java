package com.trekking.app.features.payment.dto.response;

import java.time.OffsetDateTime;

/** Refund view safe for customers and finance lists: the account number is masked. */
public record RefundResponse(
        long id,
        long bookingId,
        String status,
        long paidAmountVnd,
        Integer refundPercent,
        Long hoursBeforeDeparture,
        long requestedAmountVnd,
        Long approvedAmountVnd,
        String bankName,
        String accountNumberMasked,
        String payoutChannel,
        String payoutState,
        String decisionNote,
        OffsetDateTime approvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
