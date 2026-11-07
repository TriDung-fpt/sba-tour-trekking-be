package com.trekking.app.features.dashboard.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record BookingTrendResponse(
        String granularity,
        String basis,
        OffsetDateTime from,
        OffsetDateTime to,
        Long tourId,
        List<Point> points) {

    public record Point(
            String periodKey,
            String periodLabel,
            OffsetDateTime periodStart,
            OffsetDateTime periodEnd,
            long bookingCount,
            long passengerCount,
            long cancelledCount) {
    }
}
