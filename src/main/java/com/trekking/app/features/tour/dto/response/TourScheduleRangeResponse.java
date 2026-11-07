package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import java.time.OffsetDateTime;

public record TourScheduleRangeResponse(Long id, Long tourId, String title, OffsetDateTime startsAt,
        OffsetDateTime endsAt, ScheduleStatus status, TourStatus tourStatus, long scheduleCount,
        int capacity, int confirmedSeats, int heldSeats) {}
