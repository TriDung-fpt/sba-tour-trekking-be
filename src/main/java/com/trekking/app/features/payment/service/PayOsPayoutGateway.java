package com.trekking.app.features.payment.service;

import java.util.Optional;

public interface PayOsPayoutGateway {
    PayoutResult create(String referenceId, String idempotencyKey, long amountVnd,
                        String description, String bankBin, String accountNumber);
    Optional<PayoutResult> findByReference(String referenceId);
    Optional<PayoutResult> findById(String payoutId);

    record PayoutResult(String payoutId, String transactionReference, String state) {}
}
