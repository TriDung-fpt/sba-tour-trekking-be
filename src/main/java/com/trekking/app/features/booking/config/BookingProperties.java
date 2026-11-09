package com.trekking.app.features.booking.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.booking")
public record BookingProperties(
        @Positive int maxPageSize,
        @NotBlank @Pattern(regexp = "[A-Z0-9]+") String codeAlphabet,
        // booking_code allows 40 characters, including the fixed "BK" prefix.
        @Min(1) @Max(38) int codeLength
) {}
