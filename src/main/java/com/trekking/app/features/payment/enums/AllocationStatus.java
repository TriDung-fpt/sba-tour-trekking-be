package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum AllocationStatus {
    PENDING_REVIEW("pending_review"),
    ACCEPTED("accepted");

    @EnumeratedValue
    private final String value;

    AllocationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
