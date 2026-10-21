package com.trekking.app.features.account.service;

/**
 * Resource-bound finance authorization for the payment and settlement services.
 * Call a guard before changing a resource, within the same write transaction as
 * the mutation. The guard locks and reloads the persisted resource and derives
 * the actor from the authenticated session; callers must never supply actor IDs.
 * A passing guard does not check amounts, record transfers, or provide retry /
 * idempotency handling. Those remain the consuming financial service's duty.
 */
public interface FinancialOperationAuthorizationService {
    void requireSettlementApproval(long statementId);
    void requirePayout(long statementId);
    void requireRefundApproval(long refundId);
    void requireRefundRecording(long refundId);
}
