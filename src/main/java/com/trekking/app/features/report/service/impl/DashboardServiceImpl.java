package com.trekking.app.features.report.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.service.audit.AuditWriter;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.report.dto.request.ReportQuery;
import com.trekking.app.features.report.dto.response.DashboardResponse;
import com.trekking.app.features.report.dto.response.RevenueReportResponse;
import com.trekking.app.features.report.dto.response.TransactionReportResponse;
import com.trekking.app.features.report.repository.DashboardReportRepository;
import com.trekking.app.features.report.service.DashboardService;
import com.trekking.app.features.report.service.export.CsvExporter;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String TIMEZONE = REPORT_ZONE.getId();

    private final DashboardReportRepository reports;
    private final CsvExporter csv;
    private final AuthorizationService authorization;
    private final AuditWriter audits;

    public DashboardServiceImpl(DashboardReportRepository reports, CsvExporter csv,
                                AuthorizationService authorization, AuditWriter audits) {
        this.reports = reports;
        this.csv = csv;
        this.authorization = authorization;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(String start, String end) {
        authorization.require(Permission.DASHBOARD_READ);
        return build(start, end);
    }

    @Transactional
    public String exportCsv(String start, String end) {
        authorization.require(Permission.REPORT_EXPORT);
        var dashboard = build(start, end);
        var content = csv.toCsv(
                List.of("start", "end", "timezone", "received_count", "received_vnd",
                        "refunded_count", "refunded_vnd", "net_vnd"),
                List.of(List.of(dashboard.start(), dashboard.end(), dashboard.timezone(),
                        String.valueOf(dashboard.receivedCount()), String.valueOf(dashboard.receivedVnd()),
                        String.valueOf(dashboard.refundedCount()), String.valueOf(dashboard.refundedVnd()),
                        String.valueOf(dashboard.netVnd()))));
        audits.record("report", null, "REPORT_EXPORTED", authorization.currentAccountId(),
                "Dashboard CSV export",
                Map.of("start", start, "end", end, "timezone", TIMEZONE),
                Map.of("receivedVnd", String.valueOf(dashboard.receivedVnd()),
                        "refundedVnd", String.valueOf(dashboard.refundedVnd()),
                        "netVnd", String.valueOf(dashboard.netVnd())));
        return content;
    }

    private DashboardResponse build(String start, String end) {
        var period = period(start, end);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) byStatus.put(status.getValue(), 0L);
        for (Object[] row : reports.bookingCountsByStatus(period.start, period.end)) {
            byStatus.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        long receivedCount = reports.countReceived(period.start, period.end);
        long receivedVnd = sum(reports.sumReceived(period.start, period.end));
        long refundedCount = reports.countRefunded(period.start, period.end);
        long refundedVnd = sum(reports.sumRefunded(period.start, period.end));
        return new DashboardResponse(start, end, TIMEZONE, byStatus,
                receivedCount, receivedVnd, refundedCount, refundedVnd, receivedVnd - refundedVnd);
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse revenueReport(ReportQuery query) {
        authorization.require(Permission.DASHBOARD_READ);
        return buildRevenue(query);
    }

    @Transactional
    public String exportRevenueCsv(ReportQuery query) {
        authorization.require(Permission.REPORT_EXPORT);
        var report = buildRevenue(query);
        var rows = new ArrayList<List<String>>();
        for (var bucket : report.series()) {
            rows.add(List.of(bucket.bucket(), String.valueOf(bucket.receivedCount()),
                    String.valueOf(bucket.receivedVnd()), String.valueOf(bucket.refundedCount()),
                    String.valueOf(bucket.refundedVnd()), String.valueOf(bucket.netVnd())));
        }
        rows.add(List.of("TOTAL", String.valueOf(report.receivedCount()), String.valueOf(report.receivedVnd()),
                String.valueOf(report.refundedCount()), String.valueOf(report.refundedVnd()),
                String.valueOf(report.netVnd())));
        var content = csv.toCsv(List.of("bucket", "received_count", "received_vnd",
                "refunded_count", "refunded_vnd", "net_vnd"), rows);
        exportAudit("Revenue CSV export", filterDetails(query, "revenue"), totals(report.receivedVnd(),
                report.refundedVnd(), report.netVnd()));
        return content;
    }

    @Transactional(readOnly = true)
    public TransactionReportResponse transactionReport(ReportQuery query) {
        authorization.require(Permission.DASHBOARD_READ);
        return buildTransactions(query);
    }

    @Transactional
    public String exportTransactionCsv(ReportQuery query) {
        authorization.require(Permission.REPORT_EXPORT);
        var report = buildTransactions(query);
        var rows = new ArrayList<List<String>>();
        for (var status : report.byStatus()) {
            rows.add(List.of(status.status(), String.valueOf(status.count()), String.valueOf(status.amountVnd())));
        }
        rows.add(List.of("RECEIVED", String.valueOf(report.receivedCount()), String.valueOf(report.receivedVnd())));
        rows.add(List.of("REFUNDED", String.valueOf(report.refundedCount()), String.valueOf(report.refundedVnd())));
        var content = csv.toCsv(List.of("status", "count", "amount_vnd"), rows);
        exportAudit("Transaction CSV export", filterDetails(query, "transactions"), totals(report.receivedVnd(),
                report.refundedVnd(), report.receivedVnd() - report.refundedVnd()));
        return content;
    }

    private RevenueReportResponse buildRevenue(ReportQuery query) {
        var spec = reportPeriod(query);
        String ref = query.transactionRef() == null ? "" : query.transactionRef().trim();
        var buckets = new LinkedHashMap<LocalDate, Bucket>();
        for (LocalDate day = spec.start; day.isBefore(spec.end); day = advance(day, spec.unit)) {
            buckets.put(day, new Bucket());
        }
        fill(buckets, reports.revenueBuckets(spec.unit, spec.start.atStartOfDay(REPORT_ZONE).toOffsetDateTime(),
                spec.end.atStartOfDay(REPORT_ZONE).toOffsetDateTime(), normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref), true);
        fill(buckets, reports.refundBuckets(spec.unit, spec.start.atStartOfDay(REPORT_ZONE).toOffsetDateTime(),
                spec.end.atStartOfDay(REPORT_ZONE).toOffsetDateTime(), normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref), false);
        var series = new ArrayList<RevenueReportResponse.ReportBucket>();
        long receivedCount = 0, receivedVnd = 0, refundedCount = 0, refundedVnd = 0;
        for (var entry : buckets.entrySet()) {
            var b = entry.getValue();
            receivedCount += b.receivedCount;
            receivedVnd += b.receivedVnd;
            refundedCount += b.refundedCount;
            refundedVnd += b.refundedVnd;
            series.add(new RevenueReportResponse.ReportBucket(entry.getKey().toString(), b.receivedCount,
                    b.receivedVnd, b.refundedCount, b.refundedVnd, b.receivedVnd - b.refundedVnd));
        }
        Long previousYearNet = spec.type.equals("YEARLY") ? previousYearNet(spec, query, ref) : null;
        return new RevenueReportResponse(spec.type, spec.year, spec.month, spec.quarter, TIMEZONE, series,
                receivedCount, receivedVnd, refundedCount, refundedVnd, receivedVnd - refundedVnd, previousYearNet);
    }

    private TransactionReportResponse buildTransactions(ReportQuery query) {
        var spec = reportPeriod(query);
        String ref = query.transactionRef() == null ? "" : query.transactionRef().trim();
        var start = spec.start.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        var end = spec.end.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        Map<String, long[]> attempts = new LinkedHashMap<>();
        for (Object[] row : reports.attemptStatusTotals(start, end, normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref)) {
            attempts.put(row[0].toString(), new long[]{((Number) row[1]).longValue(), number(row[2])});
        }
        long receivedCount = 0, receivedVnd = 0, refundedCount = 0, refundedVnd = 0;
        for (Object[] row : reports.revenueBuckets("month", start, end, normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref)) {
            receivedCount += ((Number) row[1]).longValue();
            receivedVnd += number(row[2]);
        }
        for (Object[] row : reports.refundBuckets("month", start, end, normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref)) {
            refundedCount += ((Number) row[1]).longValue();
            refundedVnd += number(row[2]);
        }
        var byStatus = List.of(
                new TransactionReportResponse.StatusTotal("SUCCESS", count(attempts, "succeeded"), amount(attempts, "succeeded")),
                new TransactionReportResponse.StatusTotal("FAILED", count(attempts, "failed"), amount(attempts, "failed")),
                new TransactionReportResponse.StatusTotal("PENDING",
                        count(attempts, "created") + count(attempts, "pending"),
                        amount(attempts, "created") + amount(attempts, "pending")),
                new TransactionReportResponse.StatusTotal("EXPIRED", count(attempts, "expired"), amount(attempts, "expired")),
                new TransactionReportResponse.StatusTotal("REFUNDED", refundedCount, refundedVnd));
        var filtered = statusFilter(byStatus, query.status());
        return new TransactionReportResponse(spec.type, spec.year, spec.month, spec.quarter, TIMEZONE, filtered,
                receivedCount, receivedVnd, refundedCount, refundedVnd);
    }

    private static List<TransactionReportResponse.StatusTotal> statusFilter(
            List<TransactionReportResponse.StatusTotal> byStatus, String raw) {
        if (raw == null || raw.isBlank()) return byStatus;
        String wanted = raw.trim().toUpperCase(Locale.ROOT);
        String normalized = switch (wanted) {
            case "SUCCEEDED", "SUCCESS" -> "SUCCESS";
            case "FAILED" -> "FAILED";
            case "CREATED", "PENDING" -> "PENDING";
            case "EXPIRED" -> "EXPIRED";
            case "REFUNDED", "REFUND" -> "REFUNDED";
            default -> throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid status filter");
        };
        return byStatus.stream().filter(status -> status.status().equals(normalized)).toList();
    }

    private Long previousYearNet(ReportPeriod spec, ReportQuery query, String ref) {
        var start = spec.start.minusYears(1).atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        var end = spec.start.atStartOfDay(REPORT_ZONE).toOffsetDateTime();
        boolean any = false;
        long received = 0, refunded = 0;
        for (Object[] row : reports.revenueBuckets("month", start, end, normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref)) {
            any = true;
            received += number(row[2]);
        }
        for (Object[] row : reports.refundBuckets("month", start, end, normalize(query.method()),
                query.tourId(), query.scheduleId(), query.customerId(), ref)) {
            any = true;
            refunded += number(row[2]);
        }
        return any ? received - refunded : null;
    }

    private static void fill(Map<LocalDate, Bucket> buckets, List<Object[]> rows, boolean received) {
        for (Object[] row : rows) {
            var bucket = buckets.get(toLocalDate(row[0]));
            if (bucket == null) continue;
            if (received) {
                bucket.receivedCount = ((Number) row[1]).longValue();
                bucket.receivedVnd = number(row[2]);
            } else {
                bucket.refundedCount = ((Number) row[1]).longValue();
                bucket.refundedVnd = number(row[2]);
            }
        }
    }

    private void exportAudit(String reason, Map<String, String> details, Map<String, String> totals) {
        audits.record("report", null, "REPORT_EXPORTED", authorization.currentAccountId(), reason, details, totals);
    }

    private static Map<String, String> filterDetails(ReportQuery query, String report) {
        var details = new LinkedHashMap<String, String>();
        details.put("report", report);
        details.put("period", query.period() == null ? "" : query.period());
        if (query.year() != null) details.put("year", String.valueOf(query.year()));
        if (query.month() != null) details.put("month", String.valueOf(query.month()));
        if (query.quarter() != null) details.put("quarter", String.valueOf(query.quarter()));
        if (query.method() != null) details.put("method", query.method());
        if (query.status() != null) details.put("status", query.status());
        if (query.tourId() != null) details.put("tourId", String.valueOf(query.tourId()));
        if (query.scheduleId() != null) details.put("scheduleId", String.valueOf(query.scheduleId()));
        if (query.customerId() != null) details.put("customerId", String.valueOf(query.customerId()));
        if (query.transactionRef() != null) details.put("transactionRef", query.transactionRef());
        return details;
    }

    private static Map<String, String> totals(long receivedVnd, long refundedVnd, long netVnd) {
        var totals = new LinkedHashMap<String, String>();
        totals.put("receivedVnd", String.valueOf(receivedVnd));
        totals.put("refundedVnd", String.valueOf(refundedVnd));
        totals.put("netVnd", String.valueOf(netVnd));
        return totals;
    }

    private static long count(Map<String, long[]> attempts, String status) {
        var value = attempts.get(status);
        return value == null ? 0L : value[0];
    }

    private static long amount(Map<String, long[]> attempts, String status) {
        var value = attempts.get(status);
        return value == null ? 0L : value[1];
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "payos", "vnpay", "manual", "bank_transfer" -> value;
            default -> throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid method filter");
        };
    }

    private record ReportPeriod(String type, int year, Integer month, Integer quarter,
                                LocalDate start, LocalDate end, String unit) {}

    private static class Bucket {
        long receivedCount;
        long receivedVnd;
        long refundedCount;
        long refundedVnd;
    }

    private static ReportPeriod reportPeriod(ReportQuery query) {
        String type = query.period() == null ? "" : query.period().trim().toUpperCase(Locale.ROOT);
        Integer year = query.year();
        if (year == null || year < 2000 || year > 2100) throw invalidReportPeriod();
        return switch (type) {
            case "MONTHLY" -> {
                Integer month = query.month();
                if (month == null || month < 1 || month > 12) throw invalidReportPeriod();
                var start = LocalDate.of(year, month, 1);
                yield new ReportPeriod(type, year, month, null, start, start.plusMonths(1), "day");
            }
            case "QUARTERLY" -> {
                Integer quarter = query.quarter();
                if (quarter == null || quarter < 1 || quarter > 4) throw invalidReportPeriod();
                var start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
                yield new ReportPeriod(type, year, null, quarter, start, start.plusMonths(3), "month");
            }
            case "YEARLY" -> {
                var start = LocalDate.of(year, 1, 1);
                yield new ReportPeriod(type, year, null, null, start, start.plusYears(1), "month");
            }
            default -> throw invalidReportPeriod();
        };
    }

    private static AuthException invalidReportPeriod() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_PERIOD",
                "period must be MONTHLY with month, QUARTERLY with quarter, or YEARLY, plus year");
    }

    private static LocalDate advance(LocalDate day, String unit) {
        return "day".equals(unit) ? day.plusDays(1) : day.plusMonths(1);
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private record Period(OffsetDateTime start, OffsetDateTime end) {}

    // Reporting periods use [start, end) in Asia/Ho_Chi_Minh so adjacent periods never double count.
    private static Period period(String start, String end) {
        try {
            var first = LocalDate.parse(start);
            var last = LocalDate.parse(end);
            if (!first.isBefore(last)) throw invalidPeriod();
            return new Period(first.atStartOfDay(REPORT_ZONE).toOffsetDateTime(),
                    last.atStartOfDay(REPORT_ZONE).toOffsetDateTime());
        } catch (DateTimeException ex) {
            throw invalidPeriod();
        }
    }

    private static AuthException invalidPeriod() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                "start and end must be ISO dates with start before end");
    }

    private static long sum(BigDecimal value) { return value == null ? 0L : value.longValueExact(); }
}
