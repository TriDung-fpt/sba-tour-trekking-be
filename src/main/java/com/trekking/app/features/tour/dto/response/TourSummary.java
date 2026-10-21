package com.trekking.app.features.tour.dto.response;

import java.util.List;
import java.math.BigDecimal;

public record TourSummary(long id, String title, List<LocationResponse> destinations, String coverImageUrl,
                          Integer durationDays, String difficulty, Long startingPriceVnd,
                          BigDecimal ratingAverage, long reviewCount) {}
