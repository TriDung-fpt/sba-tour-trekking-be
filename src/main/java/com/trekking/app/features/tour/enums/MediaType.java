package com.trekking.app.features.tour.enums;

import jakarta.persistence.EnumeratedValue;

public enum MediaType {
    IMAGE("image"),
    VIDEO("video");

    @EnumeratedValue
    private final String value;

    MediaType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
