package com.trekking.app.features.company.enums;

import jakarta.persistence.EnumeratedValue;

public enum CompanyMemberRole {
    OWNER("owner"),
    MANAGER("manager");

    @EnumeratedValue
    private final String value;

    CompanyMemberRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
