package com.trekking.app.features.booking.enums;

import jakarta.persistence.EnumeratedValue;

public enum BookingStatus {
    PENDING_PAYMENT("pending_payment"),
    CONFIRMED("confirmed"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    NO_SHOW("no_show");

    @EnumeratedValue
    private final String value;

    BookingStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
