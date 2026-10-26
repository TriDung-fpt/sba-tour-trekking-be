package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select b from Booking b where b.id = :bookingId and b.customerId = :customerId")
        Optional<Booking> findOwnedForUpdate(@Param("bookingId") long bookingId,
                        @Param("customerId") long customerId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select b from Booking b where b.id = :bookingId")
        Optional<Booking> findForUpdate(@Param("bookingId") long bookingId);

        @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
        Optional<Booking> findWithScheduleById(long id);

        @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
        Optional<Booking> findWithScheduleByIdAndCustomerId(long id, long customerId);

        Optional<Booking> findByIdempotencyKey(String idempotencyKey);

        @Override
        @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
        Page<Booking> findAll(Specification<Booking> specification, Pageable pageable);

        @Query("""
                        select b.id from Booking b
                        where b.status = :status and b.holdExpiresAt <= :now
                        order by b.holdExpiresAt, b.id
                        """)
        List<Long> findDueHoldIds(@Param("status") BookingStatus status, @Param("now") OffsetDateTime now,
                        Pageable pageable);

        @Query("""
                        select b.id from Booking b
                        where b.scheduleId = :scheduleId and b.status = :status and b.holdExpiresAt <= :now
                        order by b.holdExpiresAt, b.id
                        """)
        List<Long> findDueHoldIdsForSchedule(@Param("scheduleId") long scheduleId,
                        @Param("status") BookingStatus status,
                        @Param("now") OffsetDateTime now, Pageable pageable);

        /**
         * Serializes concurrent creates that share one idempotency key, even across
         * schedules.
         */
        @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) as advisory", nativeQuery = true)
        Integer lockIdempotencyKey(@Param("key") String key);

        @Query(value = """
                        select coalesce(sum(amount_vnd), 0)
                        from trekking.customer_payments
                        where booking_id = :bookingId and allocation_status = 'accepted'
                        """, nativeQuery = true)
        BigDecimal acceptedPaymentTotalVnd(@Param("bookingId") long bookingId);

        @Query(value = """
                        select coalesce(sum(amount_vnd), 0)
                        from trekking.customer_refunds
                        where booking_id = :bookingId
                        """, nativeQuery = true)
        BigDecimal refundedTotalVnd(@Param("bookingId") long bookingId);

        /**
         * Actual accepted receipts and actual refunds; attempts and pending reviews are
         * excluded.
         */
        @Query(value = """
                        select b.id as bookingId,
                               cast(coalesce((select sum(p.amount_vnd) from trekking.customer_payments p
                                    where p.booking_id = b.id and p.allocation_status = 'accepted'), 0) as bigint) as paidVnd,
                               cast(coalesce((select sum(r.amount_vnd) from trekking.customer_refunds r
                                    where r.booking_id = b.id), 0) as bigint) as refundedVnd
                        from trekking.bookings b
                        where b.id in (:bookingIds)
                        """, nativeQuery = true)
        List<MoneyTotalsView> moneyTotals(@Param("bookingIds") Collection<Long> bookingIds);

        @Query(value = "select exists(select 1 from trekking.reviews where booking_id = :bookingId)", nativeQuery = true)
        boolean hasReview(@Param("bookingId") long bookingId);

        interface MoneyTotalsView {
                Long getBookingId();

                Long getPaidVnd();

                Long getRefundedVnd();
        }
}
