package com.trekking.app.core.security.authorization;

public enum Permission {
    ACCOUNT_READ, ACCOUNT_BLOCK, ACCOUNT_ROLES_WRITE, POLICY_MANAGE,
    TOUR_READ, TOUR_MANAGE, TOUR_COMPOSE, TOUR_PUBLISH, SCHEDULE_MANAGE, BOOKING_MANAGE, GUIDE_MANAGE,
    GUIDE_SCHEDULE_READ,
    FINANCE_READ, PAYMENT_RECORD, REFUND_APPROVE, REFUND_RECORD;

    public boolean isFinancial() {
        return switch (this) {
            case FINANCE_READ, PAYMENT_RECORD, REFUND_APPROVE, REFUND_RECORD -> true;
            default -> false;
        };
    }
}
