package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum DocumentStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    READY("ready"),
    FAILED("failed"),
    ARCHIVED("archived");

    @EnumeratedValue
    private final String value;

    DocumentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
