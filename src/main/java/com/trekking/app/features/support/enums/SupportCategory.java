package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum SupportCategory {
    BOOKING("booking"),
    PAYMENT("payment"),
    REFUND("refund"),
    STATEMENT("statement"),
    OTHER("other");

    @EnumeratedValue
    private final String value;

    SupportCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
