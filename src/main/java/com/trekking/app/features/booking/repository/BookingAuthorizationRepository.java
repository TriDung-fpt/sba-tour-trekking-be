package com.trekking.app.features.booking.repository;

import java.math.BigDecimal;

/** Existing repository contract consumed by business authorization. */
public interface BookingAuthorizationRepository {
    BigDecimal acceptedPaymentTotalVnd(long bookingId);

    boolean hasReview(long bookingId);
}
