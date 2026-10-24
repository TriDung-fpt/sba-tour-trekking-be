package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalTime;

public record UpdateActivityRequest(
        @Positive int dayNumber,
        @PositiveOrZero int displayOrder,
        LocalTime startTime,
        @NotBlank String title,
        String description,
        Long locationId
) {}
