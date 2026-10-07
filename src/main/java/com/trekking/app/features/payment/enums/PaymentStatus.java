package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum PaymentStatus {
    CREATED("created"),
    PENDING("pending"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    EXPIRED("expired");

    @EnumeratedValue
    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
