package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import java.time.OffsetDateTime;

/** Money facts plus the refund request created by Payment (null when nothing is refundable). */
public record BookingCancellationResponse(
        Long bookingId,
        String bookingCode,
        BookingStatus status,
        OffsetDateTime cancelledAt,
        String cancellationReason,
        Long cancelledBy,
        long paidAmountVnd,
        long refundedAmountVnd,
        boolean refundRequired,
        RefundResponse refund
) {}
