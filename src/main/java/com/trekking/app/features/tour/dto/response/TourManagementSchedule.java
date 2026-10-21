package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TourManagementSchedule(long id, long tourId, OffsetDateTime startsAt, OffsetDateTime endsAt, int capacity,
                                    int heldSeats, int confirmedSeats, int availableSpots, long basePriceVnd,
                                    long equipmentFeeVnd, BigDecimal depositPercent, OffsetDateTime balanceDueAt,
                                    String meetingPoint, String contactPhone, String status, List<Guide> guides) {}
