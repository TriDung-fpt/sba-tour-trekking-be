package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record BookingDetailResponse(
        long id,
        String bookingCode,
        BookingStatus status,
        BookingContactResponse contact,
        long tourId,
        String tourTitle,
        long scheduleId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        int participantCount,
        long basePriceVnd,
        long rentalFeeVnd,
        long totalPriceVnd,
        BigDecimal depositPercent,
        long depositDueVnd,
        OffsetDateTime balanceDueAt,
        OffsetDateTime holdExpiresAt,
        Map<String, Object> rental,
        List<BookingTravelerResponse> travelers,
        OffsetDateTime createdAt,
        OffsetDateTime cancelledAt,
        String cancellationReason) {}
