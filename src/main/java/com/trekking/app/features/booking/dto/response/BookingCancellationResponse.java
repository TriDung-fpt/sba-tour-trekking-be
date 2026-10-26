package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;

/** Reports money facts only; refund calculation and approval belong to Payment. */
public record BookingCancellationResponse(
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        Long cancelledBy,
        long paidAmountVnd,
        long refundedAmountVnd,
        boolean refundRequired
) {}
