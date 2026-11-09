package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking>,
        BookingAuthorizationRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedByIdAndCustomerId(long id, long customerId);

    // Keep the entry point used by account authorization; Spring Data handles the locked query.
    default Optional<Booking> findOwnedForUpdate(long bookingId, long customerId) {
        return findLockedByIdAndCustomerId(bookingId, customerId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Booking> findLockedById(long id);

    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    Optional<Booking> findWithScheduleById(long id);

    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    Optional<Booking> findWithScheduleByIdAndCustomerId(long id, long customerId);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    Page<Booking> findByCustomerId(long customerId, Pageable pageable);

    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    Page<Booking> findByCustomerIdAndStatus(long customerId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    List<Booking> findAllByCustomerIdAndStatusAndHoldExpiresAtAfterOrderByHoldExpiresAtAsc(
            Long customerId, BookingStatus status, OffsetDateTime now);

    @Override
    @EntityGraph(attributePaths = { "schedule", "schedule.tour" })
    Page<Booking> findAll(Specification<Booking> specification, Pageable pageable);

    List<Booking> findByStatusAndHoldExpiresAtLessThanEqual(
            BookingStatus status, OffsetDateTime now, Pageable pageable);

    List<Booking> findByScheduleIdAndStatusAndHoldExpiresAtLessThanEqual(
            long scheduleId, BookingStatus status, OffsetDateTime now, Pageable pageable);
}
