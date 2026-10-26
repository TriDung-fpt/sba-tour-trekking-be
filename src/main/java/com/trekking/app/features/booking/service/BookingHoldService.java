package com.trekking.app.features.booking.service;

public interface BookingHoldService {
    /** Expires due unpaid holds of one schedule; each booking commits in its own transaction. */
    int expireDueHolds(long scheduleId);
}
