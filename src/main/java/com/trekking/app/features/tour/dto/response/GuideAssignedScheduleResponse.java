package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record GuideAssignedScheduleResponse(
        long scheduleId,
        long tourId,
        String tourTitle,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String status,
        String meetingPoint,
        String contactPhone,
        int capacity,
        int confirmedSeats,
        int heldSeats,
        boolean isLead,
        OffsetDateTime assignedAt,
        int passengerCount,
        String checkInRoundStatus,
        OffsetDateTime checkInSubmittedAt,
        String checkOutRoundStatus,
        OffsetDateTime checkOutSubmittedAt
) {}
