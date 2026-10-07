package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum SupportStatus {
    NEW("new"),
    ASSIGNED("assigned"),
    WAITING("waiting"),
    RESOLVED("resolved"),
    CLOSED("closed");

    @EnumeratedValue
    private final String value;

    SupportStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
