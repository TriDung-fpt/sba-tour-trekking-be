package com.trekking.app.features.statement.enums;

import jakarta.persistence.EnumeratedValue;

public enum ItemKind {
    SALE("sale"),
    ADJUSTMENT("adjustment");

    @EnumeratedValue
    private final String value;

    ItemKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
