package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record TourCodeResponse(
        long id,
        long tourId,
        String tourTitle,
        String code,
        boolean active,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime deletedAt,
        Long deletedBy,
        String deleteReason) {}
