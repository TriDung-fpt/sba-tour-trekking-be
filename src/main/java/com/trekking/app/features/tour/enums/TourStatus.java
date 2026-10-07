package com.trekking.app.features.tour.enums;

import jakarta.persistence.EnumeratedValue;

public enum TourStatus {
    DRAFT("draft"),
    PENDING("pending"),
    PUBLISHED("published"),
    REJECTED("rejected"),
    ARCHIVED("archived");

    @EnumeratedValue
    private final String value;

    TourStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
