package com.trekking.app.features.report.dto.request;

public record ReportQuery(String period, Integer year, Integer month, Integer quarter,
                          Long tourId, Long scheduleId, String method, String status,
                          String transactionRef, Long customerId) {
}
