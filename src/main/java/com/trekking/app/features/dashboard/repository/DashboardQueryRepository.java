package com.trekking.app.features.dashboard.repository;

import com.trekking.app.features.dashboard.enums.CompareGranularity;
import com.trekking.app.features.dashboard.enums.DashboardBasis;
import com.trekking.app.features.dashboard.enums.TrendGranularity;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.CompareCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.FillCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.PeriodCount;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.StatusCounts;
import com.trekking.app.features.dashboard.repository.DashboardAggregates.TourRank;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardQueryRepository {
    private static final String VALID = "b.status IN ('confirmed', 'completed')";
    private final EntityManager entities;

    public DashboardQueryRepository(EntityManager entities) {
        this.entities = entities;
    }

    public List<PeriodCount> trend(DashboardBasis basis, TrendGranularity granularity,
                                   OffsetDateTime start, OffsetDateTime end, long tourId) {
        String event = eventColumn(basis);
        String sql = """
                SELECT %s AS period_key,
                       count(*) AS booking_count,
                       coalesce(sum(b.participant_count) FILTER (WHERE %s), 0) AS passenger_count,
                       count(*) FILTER (WHERE b.status = 'cancelled') AS cancelled_count
                FROM trekking.bookings b
                JOIN trekking.tour_schedules s ON s.id = b.schedule_id
                WHERE %s >= :rangeStart AND %s < :rangeEnd
                  AND (:tourId < 0 OR s.tour_id = :tourId)
                GROUP BY 1
                ORDER BY 1
                """.formatted(trendKey(event, granularity), VALID, event, event);
        List<?> rows = entities.createNativeQuery(sql)
                .setParameter("rangeStart", start)
                .setParameter("rangeEnd", end)
                .setParameter("tourId", tourId)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new PeriodCount(String.valueOf(values[0]), number(values[1]), number(values[2]), number(values[3]));
        }).toList();
    }

    public List<CompareCount> compare(DashboardBasis basis, CompareGranularity granularity,
                                      int baseYear, int compareYear, List<Integer> months, long tourId) {
        String event = eventColumn(basis);
        String local = local(event);
        String sql = """
                SELECT %s AS year_key,
                       %s AS period_key,
                       count(*) AS booking_count,
                       coalesce(sum(b.participant_count) FILTER (WHERE %s), 0) AS passenger_count
                FROM trekking.bookings b
                JOIN trekking.tour_schedules s ON s.id = b.schedule_id
                WHERE (%s = :baseYear OR %s = :compareYear)
                  AND CAST(extract(month FROM %s) AS int) IN (%s)
                  AND (:tourId < 0 OR s.tour_id = :tourId)
                GROUP BY 1, 2
                """.formatted(yearKey(local, granularity), compareKey(local, granularity), VALID,
                yearKey(local, granularity), yearKey(local, granularity), local, monthList(months));
        List<?> rows = entities.createNativeQuery(sql)
                .setParameter("baseYear", Integer.toString(baseYear))
                .setParameter("compareYear", Integer.toString(compareYear))
                .setParameter("tourId", tourId)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new CompareCount(String.valueOf(values[0]), String.valueOf(values[1]), number(values[2]), number(values[3]));
        }).toList();
    }

    public StatusCounts summary(DashboardBasis basis, OffsetDateTime start, OffsetDateTime end) {
        String event = eventColumn(basis);
        String sql = """
                SELECT count(*) AS total_bookings,
                       coalesce(sum(b.participant_count), 0) AS total_passengers,
                       count(*) FILTER (WHERE b.status = 'confirmed') AS confirmed_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE b.status = 'confirmed'), 0) AS confirmed_passengers,
                       count(*) FILTER (WHERE b.status = 'pending_payment') AS pending_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE b.status = 'pending_payment'), 0) AS pending_passengers,
                       count(*) FILTER (WHERE b.status = 'cancelled') AS cancelled_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE b.status = 'cancelled'), 0) AS cancelled_passengers,
                       count(*) FILTER (WHERE b.status = 'expired') AS expired_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE b.status = 'expired'), 0) AS expired_passengers,
                       count(*) FILTER (WHERE b.status = 'completed') AS completed_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE b.status = 'completed'), 0) AS completed_passengers,
                       count(*) FILTER (WHERE %s) AS valid_bookings,
                       coalesce(sum(b.participant_count) FILTER (WHERE %s), 0) AS valid_passengers
                FROM trekking.bookings b
                JOIN trekking.tour_schedules s ON s.id = b.schedule_id
                WHERE %s >= :rangeStart AND %s < :rangeEnd
                """.formatted(VALID, VALID, event, event);
        Object[] values = (Object[]) entities.createNativeQuery(sql)
                .setParameter("rangeStart", start)
                .setParameter("rangeEnd", end)
                .getSingleResult();
        return new StatusCounts(
                number(values[0]), number(values[1]), number(values[2]), number(values[3]),
                number(values[4]), number(values[5]), number(values[6]), number(values[7]),
                number(values[8]), number(values[9]), number(values[10]), number(values[11]),
                number(values[12]), number(values[13]));
    }

    public List<TourRank> topTours(DashboardBasis basis, OffsetDateTime start, OffsetDateTime end, int limit) {
        String event = eventColumn(basis);
        String sql = """
                SELECT tour_id, tour_name, booking_count, passenger_count,
                       sum(passenger_count) OVER () AS total_passengers
                FROM (
                    SELECT s.tour_id AS tour_id,
                           t.title AS tour_name,
                           count(*) FILTER (WHERE %s) AS booking_count,
                           coalesce(sum(b.participant_count) FILTER (WHERE %s), 0) AS passenger_count
                    FROM trekking.bookings b
                    JOIN trekking.tour_schedules s ON s.id = b.schedule_id
                    JOIN trekking.tours t ON t.id = s.tour_id
                    WHERE %s >= :rangeStart AND %s < :rangeEnd
                    GROUP BY s.tour_id, t.title
                ) ranked
                WHERE booking_count > 0
                ORDER BY passenger_count DESC, booking_count DESC, tour_id
                LIMIT :rowLimit
                """.formatted(VALID, VALID, event, event);
        List<?> rows = entities.createNativeQuery(sql)
                .setParameter("rangeStart", start)
                .setParameter("rangeEnd", end)
                .setParameter("rowLimit", limit)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new TourRank(number(values[0]), String.valueOf(values[1]), number(values[2]), number(values[3]), number(values[4]));
        }).toList();
    }

    public List<FillCount> fillRate(OffsetDateTime start, OffsetDateTime end, long tourId, boolean openOnly, OffsetDateTime now) {
        String openFilter = openOnly ? "AND s.status IN ('scheduled', 'ongoing')" : "";
        String sql = """
                SELECT s.id AS schedule_id,
                       t.title AS tour_name,
                       CAST(s.starts_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS date) AS departure_date,
                       s.capacity AS capacity,
                       coalesce(sum(b.participant_count) FILTER (WHERE %s), 0) AS booked_passengers,
                       coalesce(sum(b.participant_count) FILTER (
                           WHERE b.status = 'pending_payment' AND b.hold_expires_at > :asOf), 0) AS held_passengers
                FROM trekking.tour_schedules s
                JOIN trekking.tours t ON t.id = s.tour_id
                LEFT JOIN trekking.bookings b ON b.schedule_id = s.id
                WHERE s.starts_at >= :rangeStart AND s.starts_at < :rangeEnd
                  AND (:tourId < 0 OR s.tour_id = :tourId)
                  %s
                GROUP BY s.id, t.title, departure_date, s.capacity
                ORDER BY departure_date, s.id
                """.formatted(VALID, openFilter);
        List<?> rows = entities.createNativeQuery(sql)
                .setParameter("rangeStart", start)
                .setParameter("rangeEnd", end)
                .setParameter("tourId", tourId)
                .setParameter("asOf", now)
                .getResultList();
        return rows.stream().map(row -> {
            Object[] values = (Object[]) row;
            return new FillCount(number(values[0]), String.valueOf(values[1]), date(values[2]),
                    (int) number(values[3]), number(values[4]), number(values[5]));
        }).toList();
    }

    private static String eventColumn(DashboardBasis basis) {
        return basis == DashboardBasis.DEPARTURE_DATE ? "s.starts_at" : "b.created_at";
    }

    private static String local(String column) {
        return "(" + column + " AT TIME ZONE 'Asia/Ho_Chi_Minh')";
    }

    private static String trendKey(String event, TrendGranularity granularity) {
        String local = local(event);
        return switch (granularity) {
            case WEEK -> "to_char(" + local + ", 'IYYY-\"W\"IW')";
            case MONTH -> "to_char(" + local + ", 'YYYY-MM')";
            case QUARTER -> "to_char(" + local + ", 'YYYY') || '-Q' || CAST(CAST(extract(quarter from " + local + ") AS int) AS text)";
            case YEAR -> "to_char(" + local + ", 'YYYY')";
        };
    }

    private static String yearKey(String local, CompareGranularity granularity) {
        return granularity == CompareGranularity.WEEK
                ? "to_char(" + local + ", 'IYYY')"
                : "to_char(" + local + ", 'YYYY')";
    }

    private static String compareKey(String local, CompareGranularity granularity) {
        return switch (granularity) {
            case WEEK -> "to_char(" + local + ", 'IW')";
            case MONTH -> "to_char(" + local + ", 'MM')";
            case QUARTER -> "'Q' || CAST(CAST(extract(quarter from " + local + ") AS int) AS text)";
            case YEAR -> "'YEAR'";
        };
    }

    private static String monthList(List<Integer> months) {
        return months.stream().map(String::valueOf).reduce((left, right) -> left + ", " + right).orElse("0");
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDate date(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
    }
}
