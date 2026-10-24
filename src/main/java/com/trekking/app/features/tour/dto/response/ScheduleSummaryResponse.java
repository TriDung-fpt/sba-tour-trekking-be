package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.ScheduleStatus;
import java.time.OffsetDateTime;

public record ScheduleSummaryResponse(
        Long id, OffsetDateTime startsAt, OffsetDateTime endsAt, Integer capacity,
        Integer heldSeats, Integer confirmedSeats, Long basePriceVnd, Long equipmentFeeVnd,
        ScheduleStatus status) {
}
