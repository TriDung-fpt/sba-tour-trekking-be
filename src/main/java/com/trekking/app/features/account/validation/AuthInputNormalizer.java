package com.trekking.app.features.account.validation;

import java.util.Locale;

public final class AuthInputNormalizer {
    private AuthInputNormalizer() {}

    public static String email(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String fullName(String value) {
        return value == null ? null : value.trim();
    }
}
