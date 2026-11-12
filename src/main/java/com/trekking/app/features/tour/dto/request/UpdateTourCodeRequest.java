package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Renaming retires the old code (kept for history, never reused) and creates a new one. */
public record UpdateTourCodeRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,20}", message = "must be 2-20 letters or digits") String code,
        @NotBlank @Size(max = 1000) String reason) {}
