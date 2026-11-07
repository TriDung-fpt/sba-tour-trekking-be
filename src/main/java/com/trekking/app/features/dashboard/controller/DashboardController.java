package com.trekking.app.features.dashboard.controller;

import com.trekking.app.features.dashboard.dto.response.BookingCompareResponse;
import com.trekking.app.features.dashboard.dto.response.BookingTrendResponse;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse;
import com.trekking.app.features.dashboard.dto.response.ScheduleFillRateResponse;
import com.trekking.app.features.dashboard.dto.response.TopToursResponse;
import com.trekking.app.features.dashboard.enums.CompareGranularity;
import com.trekking.app.features.dashboard.enums.DashboardBasis;
import com.trekking.app.features.dashboard.enums.ScheduleFillFilter;
import com.trekking.app.features.dashboard.enums.SummaryPeriod;
import com.trekking.app.features.dashboard.enums.TrendGranularity;
import com.trekking.app.features.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/bookings/trend")
    public BookingTrendResponse trend(@RequestParam TrendGranularity granularity,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "BOOKING_DATE") DashboardBasis basis,
                                      @RequestParam(required = false) Long tourId) {
        return dashboard.trend(granularity, from, to, basis, tourId);
    }

    @GetMapping("/bookings/compare")
    public BookingCompareResponse compare(@RequestParam CompareGranularity granularity,
                                          @RequestParam int baseYear,
                                          @RequestParam int compareYear,
                                          @RequestParam(required = false) List<Integer> months,
                                          @RequestParam(defaultValue = "BOOKING_DATE") DashboardBasis basis,
                                          @RequestParam(required = false) Long tourId) {
        return dashboard.compare(granularity, baseYear, compareYear, months, basis, tourId);
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@RequestParam SummaryPeriod period,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "BOOKING_DATE") DashboardBasis basis) {
        return dashboard.summary(period, from, to, basis);
    }

    @GetMapping("/tours/top")
    public TopToursResponse topTours(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Integer limit,
                                     @RequestParam(defaultValue = "BOOKING_DATE") DashboardBasis basis) {
        return dashboard.topTours(from, to, limit, basis);
    }

    @GetMapping("/schedules/fill-rate")
    public ScheduleFillRateResponse fillRate(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(required = false) Long tourId,
                                             @RequestParam(defaultValue = "OPEN") ScheduleFillFilter status) {
        return dashboard.fillRate(from, to, tourId, status);
    }
}
