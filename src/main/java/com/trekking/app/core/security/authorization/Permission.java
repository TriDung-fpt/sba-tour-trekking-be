package com.trekking.app.core.security.authorization;

public enum Permission {
    ACCOUNT_READ, ACCOUNT_BLOCK, ACCOUNT_ROLES_WRITE, COMPANY_REVIEW, POLICY_MANAGE,
    COMPANY_READ, COMPANY_MANAGE, COMPANY_OPERATE, GUIDE_SCHEDULE_READ,
    FINANCE_READ, PAYMENT_RECORD, REFUND_APPROVE, REFUND_RECORD,
    SETTLEMENT_PREPARE, SETTLEMENT_APPROVE, PAYOUT_RECORD;

    public boolean isFinancial() {
        return switch (this) {
            case FINANCE_READ, PAYMENT_RECORD, REFUND_APPROVE, REFUND_RECORD,
                    SETTLEMENT_PREPARE, SETTLEMENT_APPROVE, PAYOUT_RECORD -> true;
            default -> false;
        };
    }
}
