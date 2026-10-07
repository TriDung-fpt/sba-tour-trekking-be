package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum PaymentPurpose {
    DEPOSIT("deposit"),
    BALANCE("balance"),
    FULL("full");

    @EnumeratedValue
    private final String value;

    PaymentPurpose(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
