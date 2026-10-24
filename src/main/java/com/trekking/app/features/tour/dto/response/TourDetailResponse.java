package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;
import java.util.List;

public record TourDetailResponse(
        Long id, String title, String description, DifficultyLevel difficulty, Integer durationDays,
        String requiredExperience, String includedServices, String excludedServices,
        String routeFileUrl, TourStatus status, List<TourMediaResponse> media) {
}
