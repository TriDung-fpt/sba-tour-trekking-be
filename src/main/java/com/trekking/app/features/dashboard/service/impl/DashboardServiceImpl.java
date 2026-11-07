package com.trekking.app.features.dashboard.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.dashboard.dto.response.BookingCompareResponse;
import com.trekking.app.features.dashboard.dto.response.BookingTrendResponse;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse.CountMetric;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse.PeriodComparison;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse.RateComparison;
import com.trekking.app.features.dashboard.dto.response.DashboardSummaryResponse.RateMetric;
import com.trekking.app.features.dashboard.dto.response.ScheduleFillRateResponse;
import com.trekking.app.features.dashboard.dto.response.TopToursResponse;
import com.trekking.app.features.dashboard.dto.response.Volume;
import com.trekking.app.features.dashboard.enums.CompareGranularity;
import com.trekking.app.features.dashboard.enums.DashboardBasis;
import com.trekking.app.features.dashboard.enums.ScheduleFillFilter;
import com.trekking.app.features.dashboard.enums.SummaryPeriod;
import com.trekking.app.features.dashboard.enums.TrendGranularity;
import com.trekking.app.features.dashboard.exception.DashboardException;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.CompareCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.FillCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.PeriodCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.StatusCounts;
import com.trekking.app.features.dashboard.repository.DashboardQueryRepository;
import com.trekking.app.features.dashboard.service.DashboardMath;
import com.trekking.app.features.dashboard.service.DashboardPeriods;
import com.trekking.app.features.dashboard.service.DashboardPeriods.CompareSlot;
import com.trekking.app.features.dashboard.service.DashboardPeriods.DateRange;
import com.trekking.app.features.dashboard.service.DashboardPeriods.Window;
import com.trekking.app.features.dashboard.service.DashboardService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final AuthorizationService authorization;
    private final DashboardQueryRepository queries;
    private final Clock clock;

    public DashboardServiceImpl(AuthorizationService authorization, DashboardQueryRepository queries, Clock clock) {
        this.authorization = authorization;
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    public BookingTrendResponse trend(TrendGranularity granularity, LocalDate from, LocalDate to,
                                      DashboardBasis basis, Long tourId) {
        authorization.require(Permission.REPORT_READ);
        OffsetDateTime now = DashboardPeriods.now(clock);
        DateRange range = DashboardPeriods.trendRange(granularity, from, to, now);
        List<Window> windows = DashboardPeriods.windows(granularity, range, granularity.maxPeriods());
        Map<String, PeriodCount> counted = new HashMap<>();
        for (PeriodCount row : queries.trend(basis, granularity, range.start(), range.end(), tourFilter(tourId))) {
            counted.put(row.key(), row);
        }
        List<BookingTrendResponse.Point> points = new ArrayList<>();
        for (Window window : windows) {
            PeriodCount row = counted.get(window.key());
            points.add(new BookingTrendResponse.Point(
                    window.key(), window.label(), window.start(), window.end(),
                    row == null ? 0 : row.bookingCount(),
                    row == null ? 0 : row.passengerCount(),
                    row == null ? 0 : row.cancelledCount()));
        }
        return new BookingTrendResponse(granularity.name(), basis.name(), range.start(), range.end(),
                tourId, List.copyOf(points));
    }

    @Override
    public BookingCompareResponse compare(CompareGranularity granularity, int baseYear, int compareYear,
                                          List<Integer> months, DashboardBasis basis, Long tourId) {
        authorization.require(Permission.REPORT_READ);
        if (baseYear == compareYear) {
            throw DashboardException.invalidCompareYear();
        }
        DashboardPeriods.requireYear(baseYear);
        DashboardPeriods.requireYear(compareYear);
        List<Integer> selectedMonths = DashboardPeriods.months(months);
        List<CompareSlot> slots = DashboardPeriods.compareSlots(granularity, baseYear, compareYear, selectedMonths);
        Map<String, CompareCount> counted = new HashMap<>();
        for (CompareCount row : queries.compare(basis, granularity, baseYear, compareYear, selectedMonths, tourFilter(tourId))) {
            counted.put(row.year() + "|" + row.period(), row);
        }
        OffsetDateTime now = DashboardPeriods.now(clock);
        List<BookingCompareResponse.Row> rows = new ArrayList<>();
        long baseBookings = 0;
        long basePassengers = 0;
        long compareBookings = 0;
        long comparePassengers = 0;
        boolean allFuture = true;
        for (CompareSlot slot : slots) {
            Volume base = volume(counted.get(baseYear + "|" + slot.sqlKey()), slot.baseExists());
            Volume compared = volume(counted.get(compareYear + "|" + slot.sqlKey()), slot.compareExists());
            boolean future = slot.baseExists() && slot.base().isFuture(now);
            boolean partial = slot.baseExists() && slot.base().isPartial(now);
            allFuture = allFuture && future;
            baseBookings += base.bookingCount();
            basePassengers += base.passengerCount();
            compareBookings += compared.bookingCount();
            comparePassengers += compared.passengerCount();
            rows.add(new BookingCompareResponse.Row(slot.period(), base, compared, delta(base, compared),
                    future ? null : DashboardMath.changePercent(base.bookingCount(), compared.bookingCount()),
                    DashboardMath.trend(base.bookingCount(), compared.bookingCount()), partial, future));
        }
        Volume baseTotal = new Volume(baseBookings, basePassengers);
        Volume compareTotal = new Volume(compareBookings, comparePassengers);
        BookingCompareResponse.Summary summary = new BookingCompareResponse.Summary(
                baseTotal, compareTotal, delta(baseTotal, compareTotal),
                allFuture ? null : DashboardMath.changePercent(baseBookings, compareBookings),
                DashboardMath.trend(baseBookings, compareBookings));
        return new BookingCompareResponse(granularity.name(), basis.name(), baseYear, compareYear,
                selectedMonths, tourId, List.copyOf(rows), summary);
    }

    @Override
    public DashboardSummaryResponse summary(SummaryPeriod period, LocalDate from, LocalDate to, DashboardBasis basis) {
        authorization.require(Permission.REPORT_READ);
        OffsetDateTime now = DashboardPeriods.now(clock);
        DateRange current = currentRange(period, from, to, now);
        DateRange previous = previousRange(period, current);
        DateRange lastYear = lastYearRange(period, current, now);
        StatusCounts currentCounts = queries.summary(basis, current.start(), current.end());
        StatusCounts previousCounts = queries.summary(basis, previous.start(), previous.end());
        StatusCounts lastYearCounts = queries.summary(basis, lastYear.start(), lastYear.end());
        boolean future = current.isFuture(now);
        boolean partial = current.isPartial(now);
        return new DashboardSummaryResponse(
                period.name(), basis.name(), current.start(), current.end(), partial, future,
                metric(currentCounts.totalBookings(), currentCounts.totalPassengers(),
                        previousCounts.totalBookings(), previousCounts.totalPassengers(),
                        lastYearCounts.totalBookings(), lastYearCounts.totalPassengers(), false, future),
                metric(currentCounts.confirmedBookings(), currentCounts.confirmedPassengers(),
                        previousCounts.confirmedBookings(), previousCounts.confirmedPassengers(),
                        lastYearCounts.confirmedBookings(), lastYearCounts.confirmedPassengers(), false, future),
                metric(currentCounts.pendingBookings(), currentCounts.pendingPassengers(),
                        previousCounts.pendingBookings(), previousCounts.pendingPassengers(),
                        lastYearCounts.pendingBookings(), lastYearCounts.pendingPassengers(), false, future),
                metric(currentCounts.cancelledBookings(), currentCounts.cancelledPassengers(),
                        previousCounts.cancelledBookings(), previousCounts.cancelledPassengers(),
                        lastYearCounts.cancelledBookings(), lastYearCounts.cancelledPassengers(), false, future),
                metric(currentCounts.expiredBookings(), currentCounts.expiredPassengers(),
                        previousCounts.expiredBookings(), previousCounts.expiredPassengers(),
                        lastYearCounts.expiredBookings(), lastYearCounts.expiredPassengers(), false, future),
                metric(currentCounts.completedBookings(), currentCounts.completedPassengers(),
                        previousCounts.completedBookings(), previousCounts.completedPassengers(),
                        lastYearCounts.completedBookings(), lastYearCounts.completedPassengers(), false, future),
                metric(currentCounts.validBookings(), currentCounts.validPassengers(),
                        previousCounts.validBookings(), previousCounts.validPassengers(),
                        lastYearCounts.validBookings(), lastYearCounts.validPassengers(), true, future),
                rate(currentCounts, previousCounts, lastYearCounts, future));
    }

    @Override
    public TopToursResponse topTours(LocalDate from, LocalDate to, Integer limit, DashboardBasis basis) {
        authorization.require(Permission.REPORT_READ);
        int resolvedLimit = limit == null ? 10 : limit;
        if (resolvedLimit < 1 || resolvedLimit > 50) {
            throw DashboardException.invalidRange("limit must be between 1 and 50");
        }
        OffsetDateTime now = DashboardPeriods.now(clock);
        LocalDate today = now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        DateRange range = DashboardPeriods.bounded(from, to, DashboardPeriods.MAX_CUSTOM_DAYS,
                new DateRange(DashboardPeriods.startOf(today.minusDays(29)), DashboardPeriods.startOf(today.plusDays(1))));
        List<TopToursResponse.Row> tours = queries.topTours(basis, range.start(), range.end(), resolvedLimit).stream()
                .map(row -> new TopToursResponse.Row(row.tourId(), row.tourName(), row.bookingCount(),
                        row.passengerCount(), DashboardMath.ratio(row.passengerCount(), row.totalPassengers())))
                .toList();
        return new TopToursResponse(basis.name(), range.start(), range.end(), tours);
    }

    @Override
    public ScheduleFillRateResponse fillRate(LocalDate from, LocalDate to, Long tourId, ScheduleFillFilter status) {
        authorization.require(Permission.REPORT_READ);
        ScheduleFillFilter resolved = status == null ? ScheduleFillFilter.OPEN : status;
        OffsetDateTime now = DashboardPeriods.now(clock);
        LocalDate today = now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        DateRange range = DashboardPeriods.bounded(from, to, DashboardPeriods.MAX_FILL_DAYS,
                new DateRange(DashboardPeriods.startOf(today), DashboardPeriods.startOf(today.plusDays(30))));
        List<FillCount> counts = queries.fillRate(range.start(), range.end(), tourFilter(tourId),
                resolved == ScheduleFillFilter.OPEN, now);
        long capacity = 0;
        long booked = 0;
        long held = 0;
        List<ScheduleFillRateResponse.Row> rows = new ArrayList<>();
        for (FillCount row : counts) {
            Double fill = DashboardMath.ratio(row.bookedPassengers(), row.capacity());
            capacity += row.capacity();
            booked += row.bookedPassengers();
            held += row.heldPassengers();
            rows.add(new ScheduleFillRateResponse.Row(row.scheduleId(), row.tourName(), row.departureDate(),
                    row.capacity(), row.bookedPassengers(), row.heldPassengers(), fill,
                    DashboardMath.fillLevel(row.capacity(), row.bookedPassengers(), fill)));
        }
        return new ScheduleFillRateResponse(range.start(), range.end(), tourId, resolved.name(), List.copyOf(rows),
                new ScheduleFillRateResponse.Summary(rows.size(), capacity, booked, held, DashboardMath.ratio(booked, capacity)));
    }

    private static Volume volume(CompareCount row, boolean exists) {
        if (!exists || row == null) {
            return new Volume(0, 0);
        }
        return new Volume(row.bookingCount(), row.passengerCount());
    }

    private static Volume delta(Volume base, Volume compared) {
        return new Volume(base.bookingCount() - compared.bookingCount(), base.passengerCount() - compared.passengerCount());
    }

    private static CountMetric metric(long bookings, long passengers, long previousBookings, long previousPassengers,
                                      long lastYearBookings, long lastYearPassengers, boolean onPassengers, boolean future) {
        long current = onPassengers ? passengers : bookings;
        long previous = onPassengers ? previousPassengers : previousBookings;
        long lastYear = onPassengers ? lastYearPassengers : lastYearBookings;
        return new CountMetric(bookings, passengers,
                new PeriodComparison(previousBookings, previousPassengers,
                        future ? null : DashboardMath.changePercent(current, previous), DashboardMath.trend(current, previous)),
                new PeriodComparison(lastYearBookings, lastYearPassengers,
                        future ? null : DashboardMath.changePercent(current, lastYear), DashboardMath.trend(current, lastYear)));
    }

    private static RateMetric rate(StatusCounts current, StatusCounts previous, StatusCounts lastYear, boolean future) {
        Double value = DashboardMath.ratio(current.cancelledBookings(), current.totalBookings());
        Double previousValue = DashboardMath.ratio(previous.cancelledBookings(), previous.totalBookings());
        Double lastYearValue = DashboardMath.ratio(lastYear.cancelledBookings(), lastYear.totalBookings());
        return new RateMetric(value,
                new RateComparison(previousValue, future ? null : DashboardMath.changePercent(value, previousValue),
                        DashboardMath.trend(value, previousValue)),
                new RateComparison(lastYearValue, future ? null : DashboardMath.changePercent(value, lastYearValue),
                        DashboardMath.trend(value, lastYearValue)));
    }

    private static long tourFilter(Long tourId) {
        if (tourId == null) {
            return -1L;
        }
        if (tourId <= 0) {
            throw DashboardException.invalidRange("tourId must be positive");
        }
        return tourId;
    }

    private static DateRange currentRange(SummaryPeriod period, LocalDate from, LocalDate to, OffsetDateTime now) {
        LocalDate today = now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        return switch (period) {
            case TODAY -> day(today);
            case THIS_WEEK -> week(today);
            case THIS_MONTH -> month(today);
            case THIS_QUARTER -> quarter(today);
            case THIS_YEAR -> year(today.getYear());
            case CUSTOM -> {
                if (from == null || to == null) {
                    throw DashboardException.invalidRange("CUSTOM requires from and to");
                }
                yield DashboardPeriods.bounded(from, to, DashboardPeriods.MAX_CUSTOM_DAYS, day(today));
            }
        };
    }

    private static DateRange previousRange(SummaryPeriod period, DateRange current) {
        LocalDate start = current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        return switch (period) {
            case TODAY -> day(start.minusDays(1));
            case THIS_WEEK -> week(start.minusWeeks(1));
            case THIS_MONTH -> month(start.minusMonths(1));
            case THIS_QUARTER -> quarter(start.minusMonths(3));
            case THIS_YEAR -> year(start.getYear() - 1);
            case CUSTOM -> {
                OffsetDateTime previousStart = current.start().minus(java.time.Duration.between(current.start(), current.end()));
                yield new DateRange(previousStart, current.start());
            }
        };
    }

    private static DateRange lastYearRange(SummaryPeriod period, DateRange current, OffsetDateTime now) {
        LocalDate start = current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        LocalDate today = now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        return switch (period) {
            case TODAY -> day(today.minusYears(1));
            case THIS_WEEK -> week(today.minusYears(1));
            case THIS_MONTH -> month(start.minusYears(1));
            case THIS_QUARTER -> quarter(start.minusYears(1));
            case THIS_YEAR -> year(start.getYear() - 1);
            case CUSTOM -> new DateRange(current.start().minusYears(1), current.end().minusYears(1));
        };
    }

    private static DateRange day(LocalDate date) {
        return new DateRange(DashboardPeriods.startOf(date), DashboardPeriods.startOf(date.plusDays(1)));
    }

    private static DateRange week(LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DateRange(DashboardPeriods.startOf(monday), DashboardPeriods.startOf(monday.plusWeeks(1)));
    }

    private static DateRange month(LocalDate date) {
        LocalDate first = date.withDayOfMonth(1);
        return new DateRange(DashboardPeriods.startOf(first), DashboardPeriods.startOf(first.plusMonths(1)));
    }

    private static DateRange quarter(LocalDate date) {
        LocalDate first = LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1);
        return new DateRange(DashboardPeriods.startOf(first), DashboardPeriods.startOf(first.plusMonths(3)));
    }

    private static DateRange year(int year) {
        LocalDate first = LocalDate.of(year, 1, 1);
        return new DateRange(DashboardPeriods.startOf(first), DashboardPeriods.startOf(first.plusYears(1)));
    }
}
