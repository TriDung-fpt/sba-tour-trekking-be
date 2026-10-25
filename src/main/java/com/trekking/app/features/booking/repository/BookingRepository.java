package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Booking-scoped reads used by the shared authorization boundary. */
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findForUpdate(@Param("bookingId") long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId and b.customerId = :customerId")
    Optional<Booking> findOwnedForUpdate(@Param("bookingId") long bookingId,
                                       @Param("customerId") long customerId);

    @EntityGraph(attributePaths = {"schedule", "schedule.tour"})
    Optional<Booking> findByIdAndCustomerId(long bookingId, long customerId);

    @EntityGraph(attributePaths = {"schedule", "schedule.tour"})
    Page<Booking> findAllByCustomerIdOrderByCreatedAtDesc(long customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT "
            + "and b.holdExpiresAt <= :now order by b.holdExpiresAt, b.id")
    List<Booking> findExpiredPendingPayments(@Param("now") OffsetDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.scheduleId = :scheduleId "
            + "and b.status = com.trekking.app.features.booking.enums.BookingStatus.PENDING_PAYMENT "
            + "and b.holdExpiresAt <= :now order by b.holdExpiresAt, b.id")
    List<Booking> findExpiredPendingPaymentsForSchedule(@Param("scheduleId") long scheduleId,
            @Param("now") OffsetDateTime now, Pageable pageable);

    @Query(value = """
            select coalesce(sum(amount_vnd), 0)
            from trekking.customer_payments
            where booking_id = :bookingId and allocation_status = 'accepted'
            """, nativeQuery = true)
    BigDecimal acceptedPaymentTotalVnd(@Param("bookingId") long bookingId);

    @Query(value = "select exists(select 1 from trekking.reviews where booking_id = :bookingId)",
            nativeQuery = true)
    boolean hasReview(@Param("bookingId") long bookingId);
}
