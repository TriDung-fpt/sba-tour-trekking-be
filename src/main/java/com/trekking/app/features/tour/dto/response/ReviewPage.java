package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewPage(List<Review> items, int page, int size, long totalItems, int totalPages,
                             BigDecimal averageTourRating, long reviewCount) {}
