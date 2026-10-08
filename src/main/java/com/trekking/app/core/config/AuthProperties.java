package com.trekking.app.core.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecret, String otpSecret, String issuer, String audience,
                             Duration accessTtl, Duration sessionTtl, boolean cookieSecure,
                             List<String> allowedOrigins, String mailFrom, String redisPrefix) {
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || otpSecret == null || otpSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET and OTP_SECRET must each contain at least 32 UTF-8 bytes");
        }
        if (jwtSecret.equals(otpSecret)) {
            throw new IllegalArgumentException("JWT_SECRET and OTP_SECRET must be different");
        }
        if (accessTtl == null || accessTtl.isNegative() || accessTtl.isZero()
                || sessionTtl == null || sessionTtl.compareTo(accessTtl) <= 0) {
            throw new IllegalArgumentException("Invalid auth token lifetimes");
        }
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .filter(s -> !s.isBlank()).map(String::trim).toList();
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("Auth CORS origins must be explicit");
        }
    }
}
