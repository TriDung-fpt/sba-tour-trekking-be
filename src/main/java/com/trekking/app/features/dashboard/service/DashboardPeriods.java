package com.trekking.app.features.dashboard.service;

import com.trekking.app.features.dashboard.enums.CompareGranularity;
import com.trekking.app.features.dashboard.enums.TrendGranularity;
import com.trekking.app.features.dashboard.exception.DashboardException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

public final class DashboardPeriods {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int MIN_YEAR = 1970;
    public static final int MAX_YEAR = 2100;
    public static final int MAX_CUSTOM_DAYS = 366 * 5;
    public static final int MAX_FILL_DAYS = 366;

    private DashboardPeriods() {
    }

    public record Window(String key, String label, OffsetDateTime start, OffsetDateTime end) {
    }

    public record DateRange(OffsetDateTime start, OffsetDateTime end) {
        public boolean contains(OffsetDateTime instant) {
            return !instant.isBefore(start) && instant.isBefore(end);
        }

        public boolean isFuture(OffsetDateTime instant) {
            return !instant.isBefore(end) ? false : instant.isBefore(start);
        }

        public boolean isPartial(OffsetDateTime instant) {
            return contains(instant);
        }
    }

    public static OffsetDateTime now(java.time.Clock clock) {
        return OffsetDateTime.ofInstant(clock.instant(), ZONE);
    }

    public static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toOffsetDateTime();
    }

    public static DateRange trendRange(TrendGranularity granularity, LocalDate from, LocalDate to, OffsetDateTime now) {
        if ((from == null) != (to == null)) {
            throw DashboardException.invalidRange("from and to must both be set or both be omitted");
        }
        DateRange range = from == null ? defaultTrendRange(granularity, now) : new DateRange(startOf(from), startOf(to));
        if (!range.start().isBefore(range.end())) {
            throw DashboardException.invalidRange("from must be before to");
        }
        windows(granularity, range, granularity.maxPeriods());
        return range;
    }

    public static List<Window> windows(TrendGranularity granularity, DateRange range, int maxPeriods) {
        List<Window> windows = new ArrayList<>();
        OffsetDateTime cursor = floor(granularity, range.start());
        while (cursor.isBefore(range.end())) {
            if (windows.size() >= maxPeriods) {
                throw DashboardException.rangeTooLarge();
            }
            OffsetDateTime next = advance(granularity, cursor);
            windows.add(new Window(key(granularity, cursor), label(granularity, cursor), cursor, next));
            cursor = next;
        }
        if (windows.isEmpty()) {
            throw DashboardException.invalidRange("from must be before to");
        }
        return windows;
    }

    public static DateRange bounded(LocalDate from, LocalDate to, int maxDays, DateRange fallback) {
        if ((from == null) != (to == null)) {
            throw DashboardException.invalidRange("from and to must both be set or both be omitted");
        }
        DateRange range = from == null ? fallback : new DateRange(startOf(from), startOf(to));
        if (!range.start().isBefore(range.end())) {
            throw DashboardException.invalidRange("from must be before to");
        }
        long days = ChronoUnit.DAYS.between(range.start(), range.end());
        if (days > maxDays) {
            throw DashboardException.rangeTooLarge();
        }
        return range;
    }

    public static void requireYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw DashboardException.invalidRange("Year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
    }

    public static List<Integer> months(List<Integer> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        }
        List<Integer> months = requested.stream().distinct().sorted().toList();
        if (months.stream().anyMatch(month -> month < 1 || month > 12)) {
            throw DashboardException.invalidRange("months must be between 1 and 12");
        }
        return months;
    }

    public static List<CompareSlot> compareSlots(CompareGranularity granularity, int baseYear, int compareYear,
                                                 List<Integer> months) {
        return switch (granularity) {
            case MONTH -> monthSlots(baseYear, compareYear, months);
            case QUARTER -> quarterSlots(baseYear, compareYear, months);
            case WEEK -> weekSlots(baseYear, compareYear, months);
            case YEAR -> List.of(new CompareSlot("YEAR", "YEAR",
                    yearRange(baseYear), yearRange(compareYear), weekExists(baseYear, 1), weekExists(compareYear, 1)));
        };
    }

    public record CompareSlot(String period, String sqlKey, DateRange base, DateRange compare,
                              boolean baseExists, boolean compareExists) {
    }

    private static List<CompareSlot> monthSlots(int baseYear, int compareYear, List<Integer> months) {
        List<CompareSlot> slots = new ArrayList<>();
        for (int month : months) {
            slots.add(new CompareSlot(String.format("%02d", month), String.format("%02d", month),
                    monthRange(baseYear, month), monthRange(compareYear, month), true, true));
        }
        return slots;
    }

    private static List<CompareSlot> quarterSlots(int baseYear, int compareYear, List<Integer> months) {
        List<CompareSlot> slots = new ArrayList<>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            int first = (quarter - 1) * 3 + 1;
            if (months.contains(first) || months.contains(first + 1) || months.contains(first + 2)) {
                String key = "Q" + quarter;
                slots.add(new CompareSlot(key, key, quarterRange(baseYear, quarter), quarterRange(compareYear, quarter), true, true));
            }
        }
        if (slots.isEmpty()) {
            throw DashboardException.invalidRange("months do not match any quarter");
        }
        return slots;
    }

    private static List<CompareSlot> weekSlots(int baseYear, int compareYear, List<Integer> months) {
        int last = Math.max(isoWeekCount(baseYear), isoWeekCount(compareYear));
        List<CompareSlot> slots = new ArrayList<>();
        for (int week = 1; week <= last; week++) {
            boolean baseExists = weekExists(baseYear, week);
            boolean compareExists = weekExists(compareYear, week);
            LocalDate probe = baseExists ? isoMonday(baseYear, week) : isoMonday(compareYear, week);
            if (probe == null || !months.contains(probe.plusDays(3).getMonthValue())) {
                continue;
            }
            String key = String.format("%02d", week);
            slots.add(new CompareSlot("W" + key, key,
                    baseExists ? new DateRange(startOf(isoMonday(baseYear, week)), startOf(isoMonday(baseYear, week).plusWeeks(1))) : null,
                    compareExists ? new DateRange(startOf(isoMonday(compareYear, week)), startOf(isoMonday(compareYear, week).plusWeeks(1))) : null,
                    baseExists, compareExists));
        }
        if (slots.isEmpty()) {
            throw DashboardException.invalidRange("months do not match any ISO week");
        }
        return slots;
    }

    private static DateRange defaultTrendRange(TrendGranularity granularity, OffsetDateTime now) {
        OffsetDateTime current = floor(granularity, now);
        OffsetDateTime end = advance(granularity, current);
        OffsetDateTime start = current;
        for (int i = 1; i < granularity.defaultPeriods(); i++) {
            start = retreat(granularity, start);
        }
        return new DateRange(start, end);
    }

    private static OffsetDateTime floor(TrendGranularity granularity, OffsetDateTime instant) {
        LocalDate date = instant.atZoneSameInstant(ZONE).toLocalDate();
        LocalDate start = switch (granularity) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1);
            case YEAR -> LocalDate.of(date.getYear(), 1, 1);
        };
        return startOf(start);
    }

    private static OffsetDateTime advance(TrendGranularity granularity, OffsetDateTime start) {
        LocalDate date = start.atZoneSameInstant(ZONE).toLocalDate();
        LocalDate next = switch (granularity) {
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
            case QUARTER -> date.plusMonths(3);
            case YEAR -> date.plusYears(1);
        };
        return startOf(next);
    }

    private static OffsetDateTime retreat(TrendGranularity granularity, OffsetDateTime start) {
        LocalDate date = start.atZoneSameInstant(ZONE).toLocalDate();
        LocalDate previous = switch (granularity) {
            case WEEK -> date.minusWeeks(1);
            case MONTH -> date.minusMonths(1);
            case QUARTER -> date.minusMonths(3);
            case YEAR -> date.minusYears(1);
        };
        return startOf(previous);
    }

    private static String key(TrendGranularity granularity, OffsetDateTime start) {
        ZonedDateTime zoned = start.atZoneSameInstant(ZONE);
        return switch (granularity) {
            case WEEK -> "%d-W%02d".formatted(zoned.get(WeekFields.ISO.weekBasedYear()),
                    zoned.get(WeekFields.ISO.weekOfWeekBasedYear()));
            case MONTH -> "%d-%02d".formatted(zoned.getYear(), zoned.getMonthValue());
            case QUARTER -> "%d-Q%d".formatted(zoned.getYear(), (zoned.getMonthValue() - 1) / 3 + 1);
            case YEAR -> Integer.toString(zoned.getYear());
        };
    }

    private static String label(TrendGranularity granularity, OffsetDateTime start) {
        ZonedDateTime zoned = start.atZoneSameInstant(ZONE);
        return switch (granularity) {
            case WEEK -> "Tuần %d/%d".formatted(zoned.get(WeekFields.ISO.weekOfWeekBasedYear()),
                    zoned.get(WeekFields.ISO.weekBasedYear()));
            case MONTH -> "Tháng %d/%d".formatted(zoned.getMonthValue(), zoned.getYear());
            case QUARTER -> "Quý %d/%d".formatted((zoned.getMonthValue() - 1) / 3 + 1, zoned.getYear());
            case YEAR -> Integer.toString(zoned.getYear());
        };
    }

    private static DateRange monthRange(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        return new DateRange(startOf(start), startOf(start.plusMonths(1)));
    }

    private static DateRange quarterRange(int year, int quarter) {
        LocalDate start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        return new DateRange(startOf(start), startOf(start.plusMonths(3)));
    }

    private static DateRange yearRange(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        return new DateRange(startOf(start), startOf(start.plusYears(1)));
    }

    private static int isoWeekCount(int year) {
        return LocalDate.of(year, 12, 28).get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    private static boolean weekExists(int year, int week) {
        return week >= 1 && week <= isoWeekCount(year);
    }

    private static LocalDate isoMonday(int year, int week) {
        if (!weekExists(year, week)) {
            return null;
        }
        return LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
