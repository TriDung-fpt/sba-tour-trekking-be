package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Codes are normalized to upper case; only letters A-Z and digits, 2-20 characters. */
public record CreateTourCodeRequest(
        @NotNull @Positive Long tourId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,20}", message = "must be 2-20 letters or digits") String code) {}
