package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;

public record BookingSummaryResponse(
        long id,
        String bookingCode,
        BookingStatus status,
        long tourId,
        String tourTitle,
        long scheduleId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int participantCount,
        long totalPriceVnd,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime createdAt) {}
