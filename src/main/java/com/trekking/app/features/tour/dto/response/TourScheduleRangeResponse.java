package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.tour.enums.TourStatus;
import java.time.OffsetDateTime;

public record TourScheduleRangeResponse(Long id, Long tourId, String title, OffsetDateTime startsAt,
                                   OffsetDateTime endsAt, ScheduleStatus status, TourStatus tourStatus) {
    public TourScheduleRangeResponse(Long id, Long tourId, String title, OffsetDateTime startsAt,
                                     OffsetDateTime endsAt, ScheduleStatus status) {
        this(id, tourId, title, startsAt, endsAt, status, null);
    }
}
