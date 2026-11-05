package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingPaymentState;
import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;

public record BookingSummaryResponse(
        Long id,
        String bookingCode,
        BookingStatus status,
        Long customerId,
        Long scheduleId,
        Long tourId,
        String tourTitle,
        OffsetDateTime startsAt,
        int participantCount,
        long totalPriceVnd,
        long paidAmountVnd,
        BookingPaymentState paymentState,
        OffsetDateTime holdExpiresAt,
        OffsetDateTime createdAt
) {}
