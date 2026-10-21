package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record TourPageResponse(List<TourSummary> items, int page, int size, long totalItems, int totalPages) {}
