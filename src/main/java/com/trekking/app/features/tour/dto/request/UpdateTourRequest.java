package com.trekking.app.features.tour.dto.request;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateTourRequest(
        @NotBlank @Size(max = 250) String title,
        String description,
        DifficultyLevel difficulty,
        @Positive Integer durationDays,
        String requiredExperience,
        String includedServices,
        String excludedServices,
        String routeFileUrl,
        TourStatus status) {
}
