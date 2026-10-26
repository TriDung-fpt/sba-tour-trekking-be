package com.trekking.app.features.report.dto.response;

import java.util.List;

public record RevenueReportResponse(String period, int year, Integer month, Integer quarter, String timezone,
                                    List<ReportBucket> series, long receivedCount, long receivedVnd,
                                    long refundedCount, long refundedVnd, long netVnd,
                                    Long previousYearNetVnd) {
    public RevenueReportResponse { series = List.copyOf(series); }

    public record ReportBucket(String bucket, long receivedCount, long receivedVnd,
                               long refundedCount, long refundedVnd, long netVnd) {}
}
