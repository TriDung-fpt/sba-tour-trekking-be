package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Participant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findAllByBookingIdOrderByIdAsc(Long bookingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :participantId and p.bookingId = :bookingId")
    Optional<Participant> lockByBooking(@Param("participantId") long participantId, @Param("bookingId") long bookingId);
}
