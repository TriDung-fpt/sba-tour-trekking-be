package com.trekking.app.features.payment.enums;

import jakarta.persistence.EnumeratedValue;

public enum RefundChannel {
    PAYOS("payos"),
    VNPAY("vnpay"),
    BANK_TRANSFER("bank_transfer");

    @EnumeratedValue
    private final String value;

    RefundChannel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
