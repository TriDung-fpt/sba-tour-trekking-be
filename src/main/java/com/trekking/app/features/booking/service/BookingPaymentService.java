package com.trekking.app.features.booking.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Booking payment totals and lifecycle transitions triggered by accepted payments. */
public interface BookingPaymentService {
    boolean confirmWhenDepositPaid(long bookingId);

    BigDecimal acceptedPaymentTotalVnd(long bookingId);

    BigDecimal refundedTotalVnd(long bookingId);

    Map<Long, BigDecimal> acceptedPaymentTotals(Collection<Long> bookingIds);

    Map<Long, BigDecimal> refundedTotals(Collection<Long> bookingIds);
}
