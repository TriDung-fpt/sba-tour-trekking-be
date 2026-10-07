package com.trekking.app.features.tour.enums;

import jakarta.persistence.EnumeratedValue;

public enum ScheduleStatus {
    SCHEDULED("scheduled"),
    ONGOING("ongoing"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    @EnumeratedValue
    private final String value;

    ScheduleStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
