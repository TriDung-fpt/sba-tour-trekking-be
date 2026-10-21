package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TourPublicSchedule(long id, OffsetDateTime startsAt, OffsetDateTime endsAt, int availableSpots,
                                 long basePriceVnd, long equipmentFeeVnd, BigDecimal depositPercent, List<String> guides) {}
