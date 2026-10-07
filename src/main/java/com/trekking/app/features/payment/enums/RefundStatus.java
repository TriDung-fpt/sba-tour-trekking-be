package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum RefundStatus {
    REQUESTED("requested"),
    APPROVED("approved"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String value;

    RefundStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
