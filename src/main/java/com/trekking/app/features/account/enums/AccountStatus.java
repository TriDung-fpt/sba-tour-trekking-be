package com.trekking.app.features.account.enums;

import jakarta.persistence.EnumeratedValue;

public enum AccountStatus {
    ACTIVE("active"),
    BLOCKED("blocked"),
    DISABLED("disabled");

    @EnumeratedValue
    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
