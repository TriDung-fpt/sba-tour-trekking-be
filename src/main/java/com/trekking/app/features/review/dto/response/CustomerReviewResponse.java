package com.trekking.app.features.review.dto.response;

import java.time.OffsetDateTime;

public record CustomerReviewResponse(
        Long id,
        Long bookingId,
        short rating,
        String comment,
        String status,
        OffsetDateTime createdAt) {
}
