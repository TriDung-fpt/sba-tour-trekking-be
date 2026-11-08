package com.trekking.app.features.review.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record TourReviewPageResponse(
        List<TourReviewItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal averageRating,
        long reviewCount) {
    public TourReviewPageResponse { items = List.copyOf(items); }
}
