package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record CreateActivityRequest(
        @Positive int dayNumber,
        @PositiveOrZero int displayOrder,
        LocalTime startTime,
        @NotBlank @Size(max = 250) String title,
        String description,
        Long locationId
) {}
