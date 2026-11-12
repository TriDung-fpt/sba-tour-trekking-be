package com.trekking.app.features.payment.service;

import java.util.Optional;

/** PayOS payout (chi hộ) adapter. Calls are only made when finance explicitly triggers them. */
public interface PayOsPayoutGateway {
    PayoutResult create(String referenceId, String idempotencyKey, long amountVnd, String description,
                        String bankBin, String accountNumber);

    Optional<PayoutResult> findByReference(String referenceId);

    record PayoutResult(String payoutId, String transactionReference, String state) {
        public boolean succeeded() { return state != null && (state.equalsIgnoreCase("SUCCEEDED") || state.equalsIgnoreCase("COMPLETED")); }
        public boolean failed() {
            return state != null && (state.equalsIgnoreCase("FAILED") || state.equalsIgnoreCase("REJECTED")
                    || state.equalsIgnoreCase("CANCELLED"));
        }
    }
}
