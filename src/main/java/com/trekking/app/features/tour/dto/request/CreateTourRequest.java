package com.trekking.app.features.tour.dto.request;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTourRequest(
        @NotBlank @Size(max = 250) String title,
        String description,
        DifficultyLevel difficulty,
        @Positive Integer durationDays,
        String requiredExperience,
        String includedServices,
        String excludedServices,
        String routeFileUrl) {
}
