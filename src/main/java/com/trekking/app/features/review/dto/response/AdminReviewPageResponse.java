package com.trekking.app.features.review.dto.response;

import java.util.List;

public record AdminReviewPageResponse(List<AdminReviewListItemResponse> items, int page, int size,
                                      long totalElements, int totalPages) {
    public AdminReviewPageResponse { items = List.copyOf(items); }
}
