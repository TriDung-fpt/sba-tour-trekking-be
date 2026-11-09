package com.trekking.app.features.dashboard.repository;

import com.trekking.app.features.tour.entity.TourSchedule;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleDashboardRepository extends JpaRepository<TourSchedule, Long> {

    @Query("""
            select s.tourId as tourId,
                   t.title as tourTitle,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s join s.tour t
            where b.createdAt >= :start and b.createdAt < :end
              and b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED)
            group by s.tourId, t.title
            order by coalesce(sum(b.participantCount), 0L) desc, count(b.id) desc, s.tourId asc
            """)
    List<TopTourStat> findTopToursByCreatedAt(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            Pageable pageable);

    @Query("""
            select s.tourId as tourId,
                   t.title as tourTitle,
                   count(b.id) as bookingCount,
                   coalesce(sum(b.participantCount), 0L) as passengerCount
            from Booking b join b.schedule s join s.tour t
            where s.startsAt >= :start and s.startsAt < :end
              and b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED)
            group by s.tourId, t.title
            order by coalesce(sum(b.participantCount), 0L) desc, count(b.id) desc, s.tourId asc
            """)
    List<TopTourStat> findTopToursByDepartureAt(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            Pageable pageable);

    @Query("""
            select s.id as scheduleId,
                   t.title as tourTitle,
                   s.startsAt as startsAt,
                   s.capacity as capacity,
                   coalesce(sum(case when b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED) then b.participantCount else 0 end), 0L) as bookedPassengers,
                   coalesce(sum(case when b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now then b.participantCount else 0 end), 0L) as heldPassengers
            from Booking b right join b.schedule s join s.tour t
            where s.startsAt >= :start and s.startsAt < :end
            group by s.id, t.title, s.startsAt, s.capacity
            order by s.startsAt asc, s.id asc
            """)
    List<ScheduleFillStat> findFillStatsAll(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("now") OffsetDateTime now);

    @Query("""
            select s.id as scheduleId,
                   t.title as tourTitle,
                   s.startsAt as startsAt,
                   s.capacity as capacity,
                   coalesce(sum(case when b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED) then b.participantCount else 0 end), 0L) as bookedPassengers,
                   coalesce(sum(case when b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now then b.participantCount else 0 end), 0L) as heldPassengers
            from Booking b right join b.schedule s join s.tour t
            where s.startsAt >= :start and s.startsAt < :end
              and s.status = com.trekking.app.features.tour.enums.ScheduleStatus.SCHEDULED
            group by s.id, t.title, s.startsAt, s.capacity
            order by s.startsAt asc, s.id asc
            """)
    List<ScheduleFillStat> findFillStatsScheduledOnly(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("now") OffsetDateTime now);

    @Query("""
            select s.id as scheduleId,
                   t.title as tourTitle,
                   s.startsAt as startsAt,
                   s.capacity as capacity,
                   coalesce(sum(case when b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED) then b.participantCount else 0 end), 0L) as bookedPassengers,
                   coalesce(sum(case when b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now then b.participantCount else 0 end), 0L) as heldPassengers
            from Booking b right join b.schedule s join s.tour t
            where s.startsAt >= :start and s.startsAt < :end
              and s.tourId = :tourId
            group by s.id, t.title, s.startsAt, s.capacity
            order by s.startsAt asc, s.id asc
            """)
    List<ScheduleFillStat> findFillStatsByTourIdAll(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("tourId") Long tourId,
            @Param("now") OffsetDateTime now);

    @Query("""
            select s.id as scheduleId,
                   t.title as tourTitle,
                   s.startsAt as startsAt,
                   s.capacity as capacity,
                   coalesce(sum(case when b.status in (com.trekking.app.features.booking.enums.BookingStatus.CONFIRMED, com.trekking.app.features.booking.enums.BookingStatus.COMPLETED) then b.participantCount else 0 end), 0L) as bookedPassengers,
                   coalesce(sum(case when b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now then b.participantCount else 0 end), 0L) as heldPassengers
            from Booking b right join b.schedule s join s.tour t
            where s.startsAt >= :start and s.startsAt < :end
              and s.status = com.trekking.app.features.tour.enums.ScheduleStatus.SCHEDULED
              and s.tourId = :tourId
            group by s.id, t.title, s.startsAt, s.capacity
            order by s.startsAt asc, s.id asc
            """)
    List<ScheduleFillStat> findFillStatsByTourIdScheduledOnly(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("tourId") Long tourId,
            @Param("now") OffsetDateTime now);

    interface TopTourStat {
        Long getTourId();
        String getTourTitle();
        Long getBookingCount();
        Long getPassengerCount();
    }

    interface ScheduleFillStat {
        Long getScheduleId();
        String getTourTitle();
        OffsetDateTime getStartsAt();
        Integer getCapacity();
        Long getBookedPassengers();
        Long getHeldPassengers();
    }
}
