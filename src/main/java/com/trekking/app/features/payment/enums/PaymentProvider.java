package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum PaymentProvider {
    PAYOS("payos"),
    VNPAY("vnpay"),
    MANUAL("manual");

    @EnumeratedValue
    private final String value;

    PaymentProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
