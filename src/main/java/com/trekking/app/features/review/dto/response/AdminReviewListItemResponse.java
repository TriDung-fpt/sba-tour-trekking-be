package com.trekking.app.features.review.dto.response;

import java.time.OffsetDateTime;

public record AdminReviewListItemResponse(
        Long id,
        Long bookingId,
        Long tourId,
        String tourName,
        Long scheduleId,
        Long customerId,
        String customerName,
        short rating,
        String comment,
        String status,
        String moderationReason,
        Long moderatedBy,
        OffsetDateTime moderatedAt,
        OffsetDateTime createdAt) {
}
