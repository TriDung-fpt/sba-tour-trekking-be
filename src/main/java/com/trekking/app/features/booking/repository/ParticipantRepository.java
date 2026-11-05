package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Participant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByBookingIdAndAccountId(long bookingId, long accountId);

    List<Participant> findAllByBookingIdOrderByIdAsc(long bookingId);

    Optional<Participant> findByIdAndBookingId(long id, long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Participant> findLockedByIdAndBookingId(long id, long bookingId);

    void deleteAllByBookingId(long bookingId);
}
