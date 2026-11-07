package com.trekking.app.features.dashboard.dto.response;

import java.time.OffsetDateTime;

public record DashboardSummaryResponse(
        String period,
        String basis,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd,
        boolean partial,
        boolean future,
        CountMetric totalBookings,
        CountMetric confirmed,
        CountMetric pendingPayment,
        CountMetric cancelled,
        CountMetric expired,
        CountMetric completed,
        CountMetric passengerCount,
        RateMetric cancellationRate) {

    public record CountMetric(
            long bookingCount,
            long passengerCount,
            PeriodComparison previousPeriod,
            PeriodComparison samePeriodLastYear) {
    }

    public record PeriodComparison(
            long bookingCount,
            long passengerCount,
            Double changePercent,
            String trend) {
    }

    public record RateMetric(Double value, RateComparison previousPeriod, RateComparison samePeriodLastYear) {
    }

    public record RateComparison(Double value, Double changePercent, String trend) {
    }
}
