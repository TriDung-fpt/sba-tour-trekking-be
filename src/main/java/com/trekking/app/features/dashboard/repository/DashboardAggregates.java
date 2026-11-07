package com.trekking.app.features.dashboard.repository;

import java.time.LocalDate;

public final class DashboardAggregates {
    private DashboardAggregates() {
    }

    public record PeriodCount(String key, long bookingCount, long passengerCount, long cancelledCount) {
    }

    public record CompareCount(String year, String period, long bookingCount, long passengerCount) {
    }

    public record StatusCounts(
            long totalBookings,
            long totalPassengers,
            long confirmedBookings,
            long confirmedPassengers,
            long pendingBookings,
            long pendingPassengers,
            long cancelledBookings,
            long cancelledPassengers,
            long expiredBookings,
            long expiredPassengers,
            long completedBookings,
            long completedPassengers,
            long validBookings,
            long validPassengers) {
    }

    public record TourRank(long tourId, String tourName, long bookingCount, long passengerCount, long totalPassengers) {
    }

    public record FillCount(long scheduleId, String tourName, LocalDate departureDate, int capacity,
                            long bookedPassengers, long heldPassengers) {
    }
}
