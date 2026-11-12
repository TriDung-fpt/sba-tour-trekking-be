package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

/** One audit entry for a tour code change: TOUR_CODE_CREATED, TOUR_CODE_RENAMED or TOUR_CODE_DELETED. */
public record TourCodeLogResponse(
        Long id,
        String action,
        Long tourCodeId,
        Long actorId,
        String actorEmail,
        String reason,
        String details,
        OffsetDateTime occurredAt) {}
