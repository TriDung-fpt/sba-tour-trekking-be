package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.ScheduleStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ScheduleResponse(
        Long id, Long tourId, OffsetDateTime startsAt, OffsetDateTime endsAt, Integer capacity,
        Integer heldSeats, Integer confirmedSeats, Long basePriceVnd, Long equipmentFeeVnd,
        BigDecimal depositPercent, OffsetDateTime balanceDueAt, String meetingPoint,
        String contactPhone, ScheduleStatus status, Integer lockVersion) {
}
