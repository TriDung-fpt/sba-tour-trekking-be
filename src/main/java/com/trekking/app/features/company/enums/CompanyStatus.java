package com.trekking.app.features.company.enums;

import jakarta.persistence.EnumeratedValue;

public enum CompanyStatus {
    PENDING("pending"),
    APPROVED("approved"),
    SUSPENDED("suspended"),
    REJECTED("rejected");

    @EnumeratedValue
    private final String value;

    CompanyStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
