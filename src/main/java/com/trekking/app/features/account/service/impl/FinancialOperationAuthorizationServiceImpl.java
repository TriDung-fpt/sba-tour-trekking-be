package com.trekking.app.features.account.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.service.FinancialOperationAuthorizationService;
import com.trekking.app.features.payment.entity.RefundRequest;
import com.trekking.app.features.payment.enums.RefundStatus;
import com.trekking.app.features.payment.repository.RefundRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class FinancialOperationAuthorizationServiceImpl implements FinancialOperationAuthorizationService {
    private final AuthorizationService authorization;
    private final RefundRequestRepository refunds;
    private final EntityManager entities;

    public FinancialOperationAuthorizationServiceImpl(AuthorizationService authorization,
            RefundRequestRepository refunds, EntityManager entities) {
        this.authorization = authorization;
        this.refunds = refunds;
        this.entities = entities;
    }

    @Override
    public void requireRefundApproval(long refundId) {
        long actor = actor(Permission.REFUND_APPROVE);
        var refund = refund(refundId);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            throw conflict("REFUND_APPROVAL_NOT_ALLOWED", "Only a requested refund can be approved");
        }
        requireDifferentActor(actor, refund.getRequestedBy());
    }

    @Override
    public void requireRefundRecording(long refundId) {
        long actor = actor(Permission.REFUND_RECORD);
        var refund = refund(refundId);
        // Processing and terminal states require M4's separate completion/retry contract.
        if (refund.getStatus() != RefundStatus.APPROVED || refund.getApprovedBy() == null
                || refund.getApprovedAt() == null) {
            throw conflict("REFUND_RECORDING_NOT_ALLOWED", "Only an approved refund can be recorded");
        }
        requireDifferentActor(actor, refund.getRequestedBy(), refund.getApprovedBy());
    }

    private long actor(Permission permission) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Financial authorization requires a write transaction");
        }
        authorization.require(permission);
        return authorization.currentAccountId();
    }

    private RefundRequest refund(long refundId) {
        var refund = refunds.findForUpdate(refundId).orElseThrow(
                FinancialOperationAuthorizationServiceImpl::notFound);
        entities.refresh(refund, LockModeType.PESSIMISTIC_WRITE);
        return refund;
    }

    private static void requireDifferentActor(long actor, Long... priorActors) {
        for (Long priorActor : priorActors) {
            if (priorActor != null && actor == priorActor.longValue()) {
                throw new AuthException(HttpStatus.FORBIDDEN, "FINANCIAL_SEPARATION_REQUIRED",
                        "A different account must perform this financial operation");
            }
        }
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
