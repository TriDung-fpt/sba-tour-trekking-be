package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Booking;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Booking-scoped reads used by the shared authorization boundary. */
public interface BookingRepository extends Repository<Booking, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId and b.customerId = :customerId")
    Optional<Booking> findOwnedForUpdate(@Param("bookingId") long bookingId,
                                       @Param("customerId") long customerId);

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
