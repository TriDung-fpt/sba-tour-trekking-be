package com.trekking.app.features.dashboard.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.booking.enums.BookingStatus;
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
import com.trekking.app.features.dashboard.repository.BookingDashboardRepository;
import com.trekking.app.features.dashboard.repository.BookingDashboardRepository.PeriodStatusStat;
import com.trekking.app.features.dashboard.repository.ScheduleDashboardRepository;
import com.trekking.app.features.dashboard.repository.ScheduleDashboardRepository.ScheduleFillStat;
import com.trekking.app.features.dashboard.repository.ScheduleDashboardRepository.TopTourStat;
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
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final AuthorizationService authorization;
    private final BookingDashboardRepository bookingRepository;
    private final ScheduleDashboardRepository scheduleRepository;
    private final Clock clock;

    public DashboardServiceImpl(AuthorizationService authorization,
                                BookingDashboardRepository bookingRepository,
                                ScheduleDashboardRepository scheduleRepository,
                                Clock clock) {
        this.authorization = authorization;
        this.bookingRepository = bookingRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
    }

    @Override
    public BookingTrendResponse trend(TrendGranularity granularity, LocalDate from, LocalDate to,
                                      DashboardBasis basis, Long tourId) {
        authorization.require(Permission.REPORT_READ);
        OffsetDateTime now = DashboardPeriods.now(clock);
        DateRange range = DashboardPeriods.trendRange(granularity, from, to, now);
        List<Window> windows = DashboardPeriods.windows(granularity, range, granularity.maxPeriods());
        Long validTourId = parseOptionalTourId(tourId);

        List<BookingTrendResponse.Point> points = new ArrayList<>(windows.size());
        for (Window window : windows) {
            List<PeriodStatusStat> stats = queryStatusStats(basis, window.start(), window.end(), validTourId);
            AggregatedMetric metric = aggregate(stats);
            points.add(new BookingTrendResponse.Point(
                    window.key(), window.label(), window.start(), window.end(),
                    metric.bookingCount, metric.passengerCount, metric.cancelledCount));
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
        Long validTourId = parseOptionalTourId(tourId);

        OffsetDateTime now = DashboardPeriods.now(clock);
        List<BookingCompareResponse.Row> rows = new ArrayList<>(slots.size());
        long baseBookings = 0;
        long basePassengers = 0;
        long compareBookings = 0;
        long comparePassengers = 0;
        boolean allFuture = true;

        for (CompareSlot slot : slots) {
            AggregatedMetric baseMetric = null;
            if (slot.baseExists()) {
                List<PeriodStatusStat> stats = queryStatusStats(basis, slot.base().start(), slot.base().end(), validTourId);
                baseMetric = aggregate(stats);
            }
            AggregatedMetric compareMetric = null;
            if (slot.compareExists()) {
                List<PeriodStatusStat> stats = queryStatusStats(basis, slot.compare().start(), slot.compare().end(), validTourId);
                compareMetric = aggregate(stats);
            }

            Volume base = volume(baseMetric, slot.baseExists());
            Volume compared = volume(compareMetric, slot.compareExists());
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

        SummaryBreakdown currentCounts = summarize(queryStatusStats(basis, current.start(), current.end(), null));
        SummaryBreakdown previousCounts = summarize(queryStatusStats(basis, previous.start(), previous.end(), null));
        SummaryBreakdown lastYearCounts = summarize(queryStatusStats(basis, lastYear.start(), lastYear.end(), null));

        boolean future = current.isFuture(now);
        boolean partial = current.isPartial(now);
        return new DashboardSummaryResponse(
                period.name(), basis.name(), current.start(), current.end(), partial, future,
                metric(currentCounts.totalBookings, currentCounts.totalPassengers,
                        previousCounts.totalBookings, previousCounts.totalPassengers,
                        lastYearCounts.totalBookings, lastYearCounts.totalPassengers, false, future),
                metric(currentCounts.confirmedBookings, currentCounts.confirmedPassengers,
                        previousCounts.confirmedBookings, previousCounts.confirmedPassengers,
                        lastYearCounts.confirmedBookings, lastYearCounts.confirmedPassengers, false, future),
                metric(currentCounts.pendingBookings, currentCounts.pendingPassengers,
                        previousCounts.pendingBookings, previousCounts.pendingPassengers,
                        lastYearCounts.pendingBookings, lastYearCounts.pendingPassengers, false, future),
                metric(currentCounts.cancelledBookings, currentCounts.cancelledPassengers,
                        previousCounts.cancelledBookings, previousCounts.cancelledPassengers,
                        lastYearCounts.cancelledBookings, lastYearCounts.cancelledPassengers, false, future),
                metric(currentCounts.expiredBookings, currentCounts.expiredPassengers,
                        previousCounts.expiredBookings, previousCounts.expiredPassengers,
                        lastYearCounts.expiredBookings, lastYearCounts.expiredPassengers, false, future),
                metric(currentCounts.completedBookings, currentCounts.completedPassengers,
                        previousCounts.completedBookings, previousCounts.completedPassengers,
                        lastYearCounts.completedBookings, lastYearCounts.completedPassengers, false, future),
                metric(currentCounts.validBookings, currentCounts.validPassengers,
                        previousCounts.validBookings, previousCounts.validPassengers,
                        lastYearCounts.validBookings, lastYearCounts.validPassengers, true, future),
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

        List<TopTourStat> stats = basis == DashboardBasis.DEPARTURE_DATE
                ? scheduleRepository.findTopToursByDepartureAt(range.start(), range.end(), PageRequest.of(0, resolvedLimit))
                : scheduleRepository.findTopToursByCreatedAt(range.start(), range.end(), PageRequest.of(0, resolvedLimit));

        long totalPassengers = stats.stream().mapToLong(TopTourStat::getPassengerCount).sum();
        List<TopToursResponse.Row> tours = stats.stream()
                .map(row -> new TopToursResponse.Row(row.getTourId(), row.getTourTitle(), row.getBookingCount(),
                        row.getPassengerCount(), DashboardMath.ratio(row.getPassengerCount(), totalPassengers)))
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

        Long validTourId = parseOptionalTourId(tourId);
        boolean scheduledOnly = resolved == ScheduleFillFilter.OPEN;

        List<ScheduleFillStat> counts;
        if (validTourId == null) {
            counts = scheduledOnly
                    ? scheduleRepository.findFillStatsScheduledOnly(range.start(), range.end(), now)
                    : scheduleRepository.findFillStatsAll(range.start(), range.end(), now);
        } else {
            counts = scheduledOnly
                    ? scheduleRepository.findFillStatsByTourIdScheduledOnly(range.start(), range.end(), validTourId, now)
                    : scheduleRepository.findFillStatsByTourIdAll(range.start(), range.end(), validTourId, now);
        }

        long capacity = 0;
        long booked = 0;
        long held = 0;
        List<ScheduleFillRateResponse.Row> rows = new ArrayList<>(counts.size());
        for (ScheduleFillStat row : counts) {
            LocalDate departureDate = row.getStartsAt().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
            Double fill = DashboardMath.ratio(row.getBookedPassengers(), row.getCapacity());
            capacity += row.getCapacity();
            booked += row.getBookedPassengers();
            held += row.getHeldPassengers();
            rows.add(new ScheduleFillRateResponse.Row(row.getScheduleId(), row.getTourTitle(), departureDate,
                    row.getCapacity(), row.getBookedPassengers(), row.getHeldPassengers(), fill,
                    DashboardMath.fillLevel(row.getCapacity(), row.getBookedPassengers(), fill)));
        }
        return new ScheduleFillRateResponse(range.start(), range.end(), tourId, resolved.name(), List.copyOf(rows),
                new ScheduleFillRateResponse.Summary(rows.size(), capacity, booked, held, DashboardMath.ratio(booked, capacity)));
    }

    private List<PeriodStatusStat> queryStatusStats(DashboardBasis basis, OffsetDateTime start, OffsetDateTime end, Long tourId) {
        if (tourId == null) {
            return basis == DashboardBasis.DEPARTURE_DATE
                    ? bookingRepository.countByDepartureAtRange(start, end)
                    : bookingRepository.countByCreatedAtRange(start, end);
        }
        return basis == DashboardBasis.DEPARTURE_DATE
                ? bookingRepository.countByDepartureAtRangeAndTourId(start, end, tourId)
                : bookingRepository.countByCreatedAtRangeAndTourId(start, end, tourId);
    }

    private static AggregatedMetric aggregate(List<PeriodStatusStat> stats) {
        AggregatedMetric metric = new AggregatedMetric();
        for (PeriodStatusStat stat : stats) {
            if (isValidBooking(stat.getStatus())) {
                metric.bookingCount += stat.getBookingCount();
                metric.passengerCount += stat.getPassengerCount();
            }
            if (stat.getStatus() == BookingStatus.CANCELLED) {
                metric.cancelledCount += stat.getBookingCount();
            }
        }
        return metric;
    }

    private static boolean isValidBooking(BookingStatus status) {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED;
    }

    private static SummaryBreakdown summarize(List<PeriodStatusStat> stats) {
        SummaryBreakdown summary = new SummaryBreakdown();
        for (PeriodStatusStat stat : stats) {
            long count = stat.getBookingCount();
            long passengers = stat.getPassengerCount();

            summary.totalBookings += count;
            summary.totalPassengers += passengers;

            BookingStatus status = stat.getStatus();
            if (status == BookingStatus.CONFIRMED) {
                summary.confirmedBookings += count;
                summary.confirmedPassengers += passengers;
            } else if (status == BookingStatus.PENDING_PAYMENT) {
                summary.pendingBookings += count;
                summary.pendingPassengers += passengers;
            } else if (status == BookingStatus.CANCELLED) {
                summary.cancelledBookings += count;
                summary.cancelledPassengers += passengers;
            } else if (status == BookingStatus.EXPIRED) {
                summary.expiredBookings += count;
                summary.expiredPassengers += passengers;
            } else if (status == BookingStatus.COMPLETED) {
                summary.completedBookings += count;
                summary.completedPassengers += passengers;
            }

            if (isValidBooking(status)) {
                summary.validBookings += count;
                summary.validPassengers += passengers;
            }
        }
        return summary;
    }

    private static Long parseOptionalTourId(Long tourId) {
        if (tourId == null) {
            return null;
        }
        if (tourId <= 0) {
            throw DashboardException.invalidRange("tourId must be positive");
        }
        return tourId;
    }

    private static Volume volume(AggregatedMetric metric, boolean exists) {
        if (!exists || metric == null) {
            return new Volume(0, 0);
        }
        return new Volume(metric.bookingCount, metric.passengerCount);
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

    private static RateMetric rate(SummaryBreakdown current, SummaryBreakdown previous, SummaryBreakdown lastYear, boolean future) {
        Double value = DashboardMath.ratio(current.cancelledBookings, current.totalBookings);
        Double previousValue = DashboardMath.ratio(previous.cancelledBookings, previous.totalBookings);
        Double lastYearValue = DashboardMath.ratio(lastYear.cancelledBookings, lastYear.totalBookings);
        return new RateMetric(value,
                new RateComparison(previousValue, future ? null : DashboardMath.changePercent(value, previousValue),
                        DashboardMath.trend(value, previousValue)),
                new RateComparison(lastYearValue, future ? null : DashboardMath.changePercent(value, lastYearValue),
                        DashboardMath.trend(value, lastYearValue)));
    }

    private static DateRange currentRange(SummaryPeriod period, LocalDate from, LocalDate to, OffsetDateTime now) {
        if (period == SummaryPeriod.CUSTOM) {
            return DashboardPeriods.bounded(from, to, DashboardPeriods.MAX_CUSTOM_DAYS,
                    DashboardPeriods.bounded(null, null, DashboardPeriods.MAX_CUSTOM_DAYS,
                            new DateRange(DashboardPeriods.startOf(now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate().minusDays(29)),
                                    DashboardPeriods.startOf(now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate().plusDays(1)))));
        }
        LocalDate today = now.atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        return switch (period) {
            case TODAY -> new DateRange(DashboardPeriods.startOf(today), DashboardPeriods.startOf(today.plusDays(1)));
            case THIS_WEEK -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(start.plusWeeks(1)));
            }
            case THIS_MONTH -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(start.plusMonths(1)));
            }
            case THIS_QUARTER -> {
                LocalDate start = LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
                yield new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(start.plusMonths(3)));
            }
            case THIS_YEAR -> {
                LocalDate start = LocalDate.of(today.getYear(), 1, 1);
                yield new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(start.plusYears(1)));
            }
            case CUSTOM -> throw new IllegalStateException();
        };
    }

    private static DateRange previousRange(SummaryPeriod period, DateRange current) {
        if (period == SummaryPeriod.CUSTOM) {
            long length = current.start().until(current.end(), java.time.temporal.ChronoUnit.SECONDS);
            return new DateRange(current.start().minusSeconds(length), current.start());
        }
        LocalDate date = current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        LocalDate start = switch (period) {
            case TODAY -> date.minusDays(1);
            case THIS_WEEK -> date.minusWeeks(1);
            case THIS_MONTH -> date.minusMonths(1);
            case THIS_QUARTER -> date.minusMonths(3);
            case THIS_YEAR -> date.minusYears(1);
            case CUSTOM -> throw new IllegalStateException();
        };
        LocalDate end = switch (period) {
            case TODAY -> start.plusDays(1);
            case THIS_WEEK -> start.plusWeeks(1);
            case THIS_MONTH -> start.plusMonths(1);
            case THIS_QUARTER -> start.plusMonths(3);
            case THIS_YEAR -> start.plusYears(1);
            case CUSTOM -> throw new IllegalStateException();
        };
        return new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(end));
    }

    private static DateRange lastYearRange(SummaryPeriod period, DateRange current, OffsetDateTime now) {
        if (period == SummaryPeriod.CUSTOM) {
            LocalDate startDate = current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate().minusYears(1);
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate(),
                    current.end().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate());
            return new DateRange(DashboardPeriods.startOf(startDate), DashboardPeriods.startOf(startDate.plusDays(days)));
        }
        LocalDate date = current.start().atZoneSameInstant(DashboardPeriods.ZONE).toLocalDate();
        LocalDate start = date.minusYears(1);
        LocalDate end = switch (period) {
            case TODAY -> start.plusDays(1);
            case THIS_WEEK -> start.plusWeeks(1);
            case THIS_MONTH -> start.plusMonths(1);
            case THIS_QUARTER -> start.plusMonths(3);
            case THIS_YEAR -> start.plusYears(1);
            case CUSTOM -> throw new IllegalStateException();
        };
        return new DateRange(DashboardPeriods.startOf(start), DashboardPeriods.startOf(end));
    }

    private static class AggregatedMetric {
        long bookingCount;
        long passengerCount;
        long cancelledCount;
    }

    private static class SummaryBreakdown {
        long totalBookings;
        long totalPassengers;
        long confirmedBookings;
        long confirmedPassengers;
        long pendingBookings;
        long pendingPassengers;
        long cancelledBookings;
        long cancelledPassengers;
        long expiredBookings;
        long expiredPassengers;
        long completedBookings;
        long completedPassengers;
        long validBookings;
        long validPassengers;
    }
}
