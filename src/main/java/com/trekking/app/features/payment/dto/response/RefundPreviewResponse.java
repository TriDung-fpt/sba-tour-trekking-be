package com.trekking.app.features.payment.dto.response;

/** What the customer would get back if they cancelled now. */
public record RefundPreviewResponse(
        long bookingId,
        long paidAmountVnd,
        long alreadyRefundedVnd,
        long hoursBeforeDeparture,
        Integer refundPercent,
        long estimatedRefundVnd,
        boolean policyTiersConfigured,
        boolean refundAccountRequired) {}
