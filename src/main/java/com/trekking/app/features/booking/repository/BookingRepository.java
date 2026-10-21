package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    Optional<Booking> findByIdAndCompanyIdIn(Long id, java.util.Collection<Long> companyIds);
    Optional<Booking> findByIdAndCustomerId(Long id, Long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.customerId = :customerId")
    Optional<Booking> lockCustomerBooking(@Param("id") long id, @Param("customerId") long customerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") long id);
    @Query(value = "select id from trekking.bookings where status = 'pending_payment' and hold_expires_at <= :now order by hold_expires_at limit 50 for update skip locked", nativeQuery = true)
    List<Long> lockExpiredIds(@Param("now") OffsetDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.companyId in :companyIds")
    Optional<Booking> lockPartnerBooking(@Param("id") long id, @Param("companyIds") java.util.Collection<Long> companyIds);

    @Query(value = """
            select b from Booking b join fetch b.schedule s join fetch s.tour t
            where b.companyId in :companyIds
              and (:status is null or (b.status = :status and (:status <> :pendingStatus or b.holdExpiresAt > :now))
                   or (:expiredOnly = true and b.status = :pendingStatus and b.holdExpiresAt <= :now))
              and (:fromDate is null or s.startsAt >= :fromDate)
              and (:toDate is null or s.startsAt < :toDate)
              and (:query is null or lower(b.bookingCode) like concat('%', :query, '%')
                or lower(b.customer.fullName) like concat('%', :query, '%')
                or exists (select p.id from Participant p where p.bookingId = b.id and lower(p.fullName) like concat('%', :query, '%')))
            """, countQuery = """
            select count(b) from Booking b join b.schedule s
            where b.companyId in :companyIds
              and (:status is null or (b.status = :status and (:status <> :pendingStatus or b.holdExpiresAt > :now))
                   or (:expiredOnly = true and b.status = :pendingStatus and b.holdExpiresAt <= :now))
              and (:fromDate is null or s.startsAt >= :fromDate)
              and (:toDate is null or s.startsAt < :toDate)
              and (:query is null or lower(b.bookingCode) like concat('%', :query, '%')
                or lower(b.customer.fullName) like concat('%', :query, '%')
                or exists (select p.id from Participant p where p.bookingId = b.id and lower(p.fullName) like concat('%', :query, '%')))
            """)
    Page<Booking> findPartnerBookings(@Param("companyIds") java.util.Collection<Long> companyIds,
            @Param("status") BookingStatus status, @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate, @Param("query") String query,
            @Param("expiredOnly") boolean expiredOnly, @Param("pendingStatus") BookingStatus pendingStatus,
            @Param("now") OffsetDateTime now, Pageable pageable);
}
