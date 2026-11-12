package com.trekking.app.features.payment.dto.response;

/**
 * What the customer would get back if they cancelled now. When {@code refundAccountLocked} is true the refund
 * goes back to the paying account ({@code lockedBankName}, {@code lockedAccountMasked}) and no account is entered.
 */
public record RefundPreviewResponse(
        long bookingId,
        long paidAmountVnd,
        long alreadyRefundedVnd,
        long hoursBeforeDeparture,
        Integer refundPercent,
        long estimatedRefundVnd,
        boolean policyTiersConfigured,
        boolean refundAccountRequired,
        boolean refundAccountLocked,
        String lockedBankName,
        String lockedAccountMasked) {}
