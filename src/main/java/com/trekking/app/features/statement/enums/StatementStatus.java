package com.trekking.app.features.statement.enums;

import jakarta.persistence.EnumeratedValue;

public enum StatementStatus {
    DRAFT("draft"),
    APPROVED("approved"),
    PAYING("paying"),
    PAID("paid"),
    FAILED("failed");

    @EnumeratedValue
    private final String value;

    StatementStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
