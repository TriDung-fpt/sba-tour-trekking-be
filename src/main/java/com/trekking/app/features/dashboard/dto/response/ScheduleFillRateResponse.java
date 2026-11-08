package com.trekking.app.features.dashboard.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleFillRateResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        Long tourId,
        String status,
        List<Row> schedules,
        Summary summary) {

    public record Row(
            long scheduleId,
            String tourName,
            LocalDate departureDate,
            int capacity,
            long bookedPassengers,
            long heldPassengers,
            Double fillRate,
            String level) {
    }

    public record Summary(
            int scheduleCount,
            long capacity,
            long bookedPassengers,
            long heldPassengers,
            Double averageFillRate) {
    }
}
