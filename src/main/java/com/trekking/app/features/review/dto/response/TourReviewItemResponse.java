package com.trekking.app.features.review.dto.response;

import java.time.OffsetDateTime;

public record TourReviewItemResponse(
        Long id,
        Long customerId,
        String customerName,
        short rating,
        String comment,
        OffsetDateTime createdAt) {
}
