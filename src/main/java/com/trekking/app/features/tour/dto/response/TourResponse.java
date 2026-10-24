package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;

public record TourResponse(
        Long id, String title, String description, DifficultyLevel difficulty, Integer durationDays,
        String requiredExperience, String includedServices, String excludedServices,
        String routeFileUrl, TourStatus status) {
}
