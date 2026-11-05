package com.trekking.app.features.booking.dto.response;

import java.time.OffsetDateTime;

public record ScheduleAvailabilityResponse(
        Long scheduleId,
        Long tourId,
        OffsetDateTime startsAt,
        int capacity,
        int heldSeats,
        int confirmedSeats,
        int availableSeats,
        boolean bookable
) {}
