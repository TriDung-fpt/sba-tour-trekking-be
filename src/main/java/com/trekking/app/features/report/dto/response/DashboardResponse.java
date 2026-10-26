package com.trekking.app.features.report.dto.response;

import java.util.Map;

public record DashboardResponse(String start, String end, String timezone, Map<String, Long> bookingsByStatus,
                                long receivedCount, long receivedVnd, long refundedCount, long refundedVnd,
                                long netVnd) {
    public DashboardResponse { bookingsByStatus = Map.copyOf(bookingsByStatus); }
}
