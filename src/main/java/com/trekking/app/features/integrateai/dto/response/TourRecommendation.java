package com.trekking.app.features.integrateai.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TourRecommendation(long tourId, String title, String difficulty, int durationDays,
                                 String coverImageUrl, BigDecimal priceFromVnd, int partySize,
                                 BigDecimal estimatedTotalVnd,
                                 List<Departure> departures, List<String> locations,
                                 String reason) {
    public record Departure(OffsetDateTime startsAt, int availableSeats,
                            BigDecimal basePriceVnd, BigDecimal equipmentFeeVnd) {}
}
