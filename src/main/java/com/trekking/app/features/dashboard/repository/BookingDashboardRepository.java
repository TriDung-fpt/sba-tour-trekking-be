package com.trekking.app.features.dashboard.repository;

import com.trekking.app.features.booking.entity.Booking;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingDashboardRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b.status as status,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s
            where b.createdAt >= :start and b.createdAt < :end
            group by b.status
            """)
    List<PeriodStatusStat> countByCreatedAtRange(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            select b.status as status,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s
            where s.startsAt >= :start and s.startsAt < :end
            group by b.status
            """)
    List<PeriodStatusStat> countByDepartureAtRange(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    @Query("""
            select b.status as status,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s
            where b.createdAt >= :start and b.createdAt < :end
              and s.tourId = :tourId
            group by b.status
            """)
    List<PeriodStatusStat> countByCreatedAtRangeAndTourId(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("tourId") Long tourId);

    @Query("""
            select b.status as status,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s
            where s.startsAt >= :start and s.startsAt < :end
              and s.tourId = :tourId
            group by b.status
            """)
    List<PeriodStatusStat> countByDepartureAtRangeAndTourId(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("tourId") Long tourId);

    interface PeriodStatusStat {
        com.trekking.app.features.booking.enums.BookingStatus getStatus();
        Long getBookingCount();
        Long getPassengerCount();
    }
}
