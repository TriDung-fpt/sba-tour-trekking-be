package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;

public record TourSummaryResponse(
        Long id, String title, DifficultyLevel difficulty, Integer durationDays,
        TourStatus status, String coverImageUrl) {
}
