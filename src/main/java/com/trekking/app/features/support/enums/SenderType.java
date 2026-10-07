package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum SenderType {
    USER("user"),
    STAFF("staff"),
    AI("ai"),
    SYSTEM("system");

    @EnumeratedValue
    private final String value;

    SenderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
