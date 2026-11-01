package com.trekking.app.features.booking.service;

/** Booking-owned lifecycle transitions triggered by accepted customer payments. */
public interface BookingPaymentService {
    boolean confirmWhenDepositPaid(long bookingId);
}
