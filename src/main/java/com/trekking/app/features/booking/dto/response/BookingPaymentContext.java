package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Internal contract for the payment service; never accepts client-calculated prices. */
public record BookingPaymentContext(
        long bookingId, long customerId, BookingStatus status,
        long totalPriceVnd, BigDecimal depositPercent, long depositDueVnd,
        OffsetDateTime holdExpiresAt, OffsetDateTime balanceDueAt) {}
