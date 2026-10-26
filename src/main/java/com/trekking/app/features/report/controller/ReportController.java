package com.trekking.app.features.report.controller;

import com.trekking.app.features.report.dto.request.ReportQuery;
import com.trekking.app.features.report.dto.response.RevenueReportResponse;
import com.trekking.app.features.report.dto.response.TransactionReportResponse;
import com.trekking.app.features.report.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {
    private final DashboardService service;

    public ReportController(DashboardService service) { this.service = service; }

    @GetMapping("/revenue")
    public RevenueReportResponse revenue(@RequestParam(required = false) String period,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month,
                                         @RequestParam(required = false) Integer quarter,
                                         @RequestParam(required = false) Long tourId,
                                         @RequestParam(required = false) Long scheduleId,
                                         @RequestParam(required = false) String method,
                                         @RequestParam(required = false) String transactionRef,
                                         @RequestParam(required = false) Long customerId) {
        return service.revenueReport(query(period, year, month, quarter, tourId, scheduleId,
                method, null, transactionRef, customerId));
    }

    @GetMapping("/revenue/export")
    public ResponseEntity<String> exportRevenue(@RequestParam(required = false) String period,
                                                @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(required = false) Integer quarter,
                                                @RequestParam(required = false) Long tourId,
                                                @RequestParam(required = false) Long scheduleId,
                                                @RequestParam(required = false) String method,
                                                @RequestParam(required = false) String transactionRef,
                                                @RequestParam(required = false) Long customerId) {
        var query = query(period, year, month, quarter, tourId, scheduleId, method, null,
                transactionRef, customerId);
        return csvResponse("revenue_" + filenameSuffix(query) + ".csv", service.exportRevenueCsv(query));
    }

    @GetMapping("/transactions")
    public TransactionReportResponse transactions(@RequestParam(required = false) String period,
                                                  @RequestParam(required = false) Integer year,
                                                  @RequestParam(required = false) Integer month,
                                                  @RequestParam(required = false) Integer quarter,
                                                  @RequestParam(required = false) Long tourId,
                                                  @RequestParam(required = false) Long scheduleId,
                                                  @RequestParam(required = false) String method,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String transactionRef,
                                                  @RequestParam(required = false) Long customerId) {
        return service.transactionReport(query(period, year, month, quarter, tourId, scheduleId,
                method, status, transactionRef, customerId));
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<String> exportTransactions(@RequestParam(required = false) String period,
                                                     @RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month,
                                                     @RequestParam(required = false) Integer quarter,
                                                     @RequestParam(required = false) Long tourId,
                                                     @RequestParam(required = false) Long scheduleId,
                                                     @RequestParam(required = false) String method,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String transactionRef,
                                                     @RequestParam(required = false) Long customerId) {
        var query = query(period, year, month, quarter, tourId, scheduleId, method, status,
                transactionRef, customerId);
        return csvResponse("transactions_" + filenameSuffix(query) + ".csv", service.exportTransactionCsv(query));
    }

    private static ReportQuery query(String period, Integer year, Integer month, Integer quarter,
                                     Long tourId, Long scheduleId, String method, String status,
                                     String transactionRef, Long customerId) {
        return new ReportQuery(period, year, month, quarter, tourId, scheduleId, method, status,
                transactionRef, customerId);
    }

    private static String filenameSuffix(ReportQuery query) {
        String suffix = query.year() == null ? "invalid" : String.valueOf(query.year());
        if (query.month() != null) suffix += "_" + query.month();
        if (query.quarter() != null) suffix += "_Q" + query.quarter();
        return suffix;
    }

    private static ResponseEntity<String> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
