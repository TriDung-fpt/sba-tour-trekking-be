package com.trekking.app.features.report.repository;

import com.trekking.app.features.booking.entity.Booking;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Aggregation queries for the admin dashboard; computed in the database, not in Java. */
public interface DashboardReportRepository extends Repository<Booking, Long> {
    @Query(value = """
            select status, count(*) from trekking.bookings
            where created_at >= :start and created_at < :end
            group by status
            """, nativeQuery = true)
    List<Object[]> bookingCountsByStatus(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = """
            select count(*) from trekking.customer_payments
            where allocation_status = 'accepted' and received_at >= :start and received_at < :end
            """, nativeQuery = true)
    long countReceived(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = """
            select coalesce(sum(amount_vnd), 0) from trekking.customer_payments
            where allocation_status = 'accepted' and received_at >= :start and received_at < :end
            """, nativeQuery = true)
    BigDecimal sumReceived(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = """
            select count(*) from trekking.customer_refunds
            where refunded_at >= :start and refunded_at < :end
            """, nativeQuery = true)
    long countRefunded(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = """
            select coalesce(sum(amount_vnd), 0) from trekking.customer_refunds
            where refunded_at >= :start and refunded_at < :end
            """, nativeQuery = true)
    BigDecimal sumRefunded(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query(value = """
            select date_trunc(:unit, p.received_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date as bucket,
                   count(*) as cnt, coalesce(sum(p.amount_vnd), 0) as amount
            from trekking.customer_payments p
            join trekking.bookings b on b.id = p.booking_id
            join trekking.tour_schedules s on s.id = b.schedule_id
            where p.allocation_status = 'accepted'
              and p.received_at >= :start and p.received_at < :end
              and (:method is null or p.provider = :method)
              and (:tourId is null or s.tour_id = :tourId)
              and (:scheduleId is null or b.schedule_id = :scheduleId)
              and (:customerId is null or b.customer_id = :customerId)
              and lower(p.transaction_reference) like lower(concat('%', :transactionRef, '%'))
            group by 1
            order by bucket
            """, nativeQuery = true)
    List<Object[]> revenueBuckets(@Param("unit") String unit, @Param("start") OffsetDateTime start,
                                  @Param("end") OffsetDateTime end, @Param("method") String method,
                                  @Param("tourId") Long tourId, @Param("scheduleId") Long scheduleId,
                                  @Param("customerId") Long customerId,
                                  @Param("transactionRef") String transactionRef);

    @Query(value = """
            select date_trunc(:unit, r.refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date as bucket,
                   count(*) as cnt, coalesce(sum(r.amount_vnd), 0) as amount
            from trekking.customer_refunds r
            join trekking.bookings b on b.id = r.booking_id
            join trekking.tour_schedules s on s.id = b.schedule_id
            where r.refunded_at >= :start and r.refunded_at < :end
              and (:method is null or r.channel = :method)
              and (:tourId is null or s.tour_id = :tourId)
              and (:scheduleId is null or b.schedule_id = :scheduleId)
              and (:customerId is null or b.customer_id = :customerId)
              and lower(r.transaction_reference) like lower(concat('%', :transactionRef, '%'))
            group by 1
            order by bucket
            """, nativeQuery = true)
    List<Object[]> refundBuckets(@Param("unit") String unit, @Param("start") OffsetDateTime start,
                                 @Param("end") OffsetDateTime end, @Param("method") String method,
                                 @Param("tourId") Long tourId, @Param("scheduleId") Long scheduleId,
                                 @Param("customerId") Long customerId,
                                 @Param("transactionRef") String transactionRef);

    @Query(value = """
            select a.status as status, count(*) as cnt,
                   coalesce(sum(a.requested_amount_vnd), 0) as amount
            from trekking.payment_attempts a
            join trekking.bookings b on b.id = a.booking_id
            join trekking.tour_schedules s on s.id = b.schedule_id
            where a.created_at >= :start and a.created_at < :end
              and (:method is null or a.provider = :method)
              and (:tourId is null or s.tour_id = :tourId)
              and (:scheduleId is null or b.schedule_id = :scheduleId)
              and (:customerId is null or b.customer_id = :customerId)
              and lower(a.merchant_reference) like lower(concat('%', :transactionRef, '%'))
            group by a.status
            """, nativeQuery = true)
    List<Object[]> attemptStatusTotals(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end,
                                       @Param("method") String method, @Param("tourId") Long tourId,
                                       @Param("scheduleId") Long scheduleId, @Param("customerId") Long customerId,
                                       @Param("transactionRef") String transactionRef);
}
