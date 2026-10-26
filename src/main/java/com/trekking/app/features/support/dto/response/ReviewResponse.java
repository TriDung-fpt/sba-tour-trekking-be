package com.trekking.app.features.support.dto.response;

import java.time.OffsetDateTime;

public record ReviewResponse(Long id, Long bookingId, Long authorId, short tourRating, short serviceRating,
                             String content, String status, Long moderatedBy, String moderationReason,
                             OffsetDateTime createdAt) {
}
