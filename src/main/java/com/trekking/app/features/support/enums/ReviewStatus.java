package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum ReviewStatus {
    VISIBLE("visible"),
    HIDDEN("hidden");

    @EnumeratedValue
    private final String value;

    ReviewStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
