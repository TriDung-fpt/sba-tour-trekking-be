package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum ChatStatus {
    AI("ai"),
    WAITING_STAFF("waiting_staff"),
    STAFF("staff"),
    CLOSED("closed");

    @EnumeratedValue
    private final String value;

    ChatStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
