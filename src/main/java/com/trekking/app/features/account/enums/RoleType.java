package com.trekking.app.features.account.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

public enum RoleType {
    CUSTOMER("customer"),
    GUIDE("guide"),
    ADMIN("admin");

    @EnumeratedValue
    private final String value;

    RoleType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RoleType fromValue(String value) {
        for (RoleType role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
