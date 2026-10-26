package com.trekking.app.features.report.service;

import com.trekking.app.features.report.dto.request.ReportQuery;
import com.trekking.app.features.report.dto.response.DashboardResponse;
import com.trekking.app.features.report.dto.response.RevenueReportResponse;
import com.trekking.app.features.report.dto.response.TransactionReportResponse;

public interface DashboardService {
    DashboardResponse dashboard(String start, String end);
    String exportCsv(String start, String end);
    RevenueReportResponse revenueReport(ReportQuery query);
    TransactionReportResponse transactionReport(ReportQuery query);
    String exportRevenueCsv(ReportQuery query);
    String exportTransactionCsv(ReportQuery query);
}
