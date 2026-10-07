package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum ContentStatus {
    VISIBLE("visible"),
    HIDDEN("hidden");

    @EnumeratedValue
    private final String value;

    ContentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
