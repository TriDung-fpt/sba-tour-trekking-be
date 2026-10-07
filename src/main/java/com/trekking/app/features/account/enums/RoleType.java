package com.trekking.app.features.account.enums;

import jakarta.persistence.EnumeratedValue;

public enum RoleType {
    CUSTOMER("customer"),
    COMPANY_MANAGER("company_manager"),
    GUIDE("guide"),
    SUPPORT_STAFF("support_staff"),
    CONTENT_STAFF("content_staff"),
    ACCOUNTANT("accountant"),
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
