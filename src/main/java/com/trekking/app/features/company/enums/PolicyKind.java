package com.trekking.app.features.company.enums;

import jakarta.persistence.EnumeratedValue;

public enum PolicyKind {
    COOPERATION("cooperation"),
    CANCELLATION("cancellation"),
    PAYMENT("payment"),
    GENERAL("general");

    @EnumeratedValue
    private final String value;

    PolicyKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
