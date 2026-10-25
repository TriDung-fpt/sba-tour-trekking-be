package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Participant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    List<Participant> findAllByBookingIdOrderByIdAsc(long bookingId);
    java.util.Optional<Participant> findByBookingIdAndAccountId(long bookingId, long accountId);
}
