package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum GatewayProvider {
    PAYOS("payos"),
    VNPAY("vnpay");

    @EnumeratedValue
    private final String value;

    GatewayProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
