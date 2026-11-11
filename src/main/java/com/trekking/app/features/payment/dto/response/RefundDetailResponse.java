package com.trekking.app.features.payment.dto.response;

/** Finance-only view with the decrypted destination; null once the refund is closed and wiped. */
public record RefundDetailResponse(
        RefundResponse refund,
        String bookingCode,
        String reason,
        String bankBin,
        String accountNumber,
        String accountHolderName,
        String payoutReferenceId,
        String payoutId,
        String transactionReference,
        String evidenceUrl,
        boolean payosPayoutAvailable) {}
