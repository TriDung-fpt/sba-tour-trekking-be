package com.trekking.app.features.support.enums;

import jakarta.persistence.EnumeratedValue;

public enum DocumentFileType {
    PDF("pdf"),
    DOCX("docx"),
    TXT("txt");

    @EnumeratedValue
    private final String value;

    DocumentFileType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
