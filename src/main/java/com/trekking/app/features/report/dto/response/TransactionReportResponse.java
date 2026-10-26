package com.trekking.app.features.report.dto.response;

import java.util.List;

public record TransactionReportResponse(String period, int year, Integer month, Integer quarter, String timezone,
                                        List<StatusTotal> byStatus, long receivedCount, long receivedVnd,
                                        long refundedCount, long refundedVnd) {
    public TransactionReportResponse { byStatus = List.copyOf(byStatus); }

    public record StatusTotal(String status, long count, long amountVnd) {}
}
