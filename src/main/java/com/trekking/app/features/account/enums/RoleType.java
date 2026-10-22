package com.trekking.app.features.account.enums;

import jakarta.persistence.EnumeratedValue;

public enum RoleType {
    CUSTOMER("customer"),
    GUIDE("guide"),
    ADMIN("admin");

    @EnumeratedValue
    private final String value;

    RoleType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
