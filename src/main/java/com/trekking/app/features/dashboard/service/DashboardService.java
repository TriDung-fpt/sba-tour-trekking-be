package com.trekking.app.features.dashboard.service;

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
import java.time.LocalDate;
import java.util.List;

public interface DashboardService {
    BookingTrendResponse trend(TrendGranularity granularity, LocalDate from, LocalDate to, DashboardBasis basis, Long tourId);

    BookingCompareResponse compare(CompareGranularity granularity, int baseYear, int compareYear, List<Integer> months,
                                   DashboardBasis basis, Long tourId);

    DashboardSummaryResponse summary(SummaryPeriod period, LocalDate from, LocalDate to, DashboardBasis basis);

    TopToursResponse topTours(LocalDate from, LocalDate to, Integer limit, DashboardBasis basis);

    ScheduleFillRateResponse fillRate(LocalDate from, LocalDate to, Long tourId, ScheduleFillFilter status);
}
