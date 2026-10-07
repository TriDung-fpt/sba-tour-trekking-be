package com.trekking.app.features.tour.enums;

import jakarta.persistence.EnumeratedValue;

public enum DifficultyLevel {
    EASY("easy"),
    MODERATE("moderate"),
    HARD("hard"),
    EXPERT("expert");

    @EnumeratedValue
    private final String value;

    DifficultyLevel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
