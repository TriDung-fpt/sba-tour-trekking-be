package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingHoldExpiryScheduler {
    private final BookingService bookings;

    public BookingHoldExpiryScheduler(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${app.booking.expiry-scan-ms:60000}")
    public void expirePendingBookings() {
        bookings.expirePendingBookings();
    }
}
