package com.trekking.app.features.tour.enums;

import jakarta.persistence.EnumeratedValue;

public enum EquipmentStatus {
    ACTIVE("active"), MAINTENANCE("maintenance"), DELETED("deleted");

    @EnumeratedValue
    private final String value;

    EquipmentStatus(String value) {
        this.value = value;
    }
}
