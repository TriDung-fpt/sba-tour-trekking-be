package com.trekking.app.features.booking.repository;

import com.trekking.app.features.booking.entity.Participant;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ParticipantRepository extends Repository<Participant, Long> {
    Optional<Participant> findByBookingIdAndAccountId(long bookingId, long accountId);
}
