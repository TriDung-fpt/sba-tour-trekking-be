package com.trekking.app.features.dashboard.dto.response;

import java.util.List;

public record BookingCompareResponse(
        String granularity,
        String basis,
        int baseYear,
        int compareYear,
        List<Integer> months,
        Long tourId,
        List<Row> rows,
        Summary summary) {

    public record Row(
            String period,
            Volume baseValue,
            Volume compareValue,
            Volume delta,
            Double changePercent,
            String trend,
            boolean partial,
            boolean future) {
    }

    public record Summary(
            Volume baseValue,
            Volume compareValue,
            Volume delta,
            Double changePercent,
            String trend) {
    }
}
