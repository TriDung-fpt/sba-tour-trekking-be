package com.trekking.app.features.tour.dto.response;

import java.time.LocalTime;

public record ActivityResponse(
        Long id,
        int dayNumber,
        int displayOrder,
        LocalTime startTime,
        String title,
        String description,
        Long locationId
) {}
