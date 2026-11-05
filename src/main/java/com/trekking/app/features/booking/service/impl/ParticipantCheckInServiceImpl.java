package com.trekking.app.features.booking.service.impl;

import com.trekking.app.features.booking.dto.request.ParticipantCheckInCommand;
import com.trekking.app.features.booking.dto.response.ParticipantCheckInResponse;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.exception.BookingErrorCode;
import com.trekking.app.features.booking.exception.BookingException;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.ParticipantCheckInService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantCheckInServiceImpl implements ParticipantCheckInService {
    private final BookingRepository bookings;
    private final ParticipantRepository participants;
    private final Clock clock;

    public ParticipantCheckInServiceImpl(BookingRepository bookings, ParticipantRepository participants, Clock clock) {
        this.bookings = bookings;
        this.participants = participants;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipantCheckInResponse checkIn(ParticipantCheckInCommand command) {
        var booking = bookings.findForUpdate(command.bookingId()).orElseThrow(BookingException::notFound);
        if (!booking.getScheduleId().equals(command.scheduleId())) {
            throw BookingException.notFound();
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingException(BookingErrorCode.INVALID_BOOKING_TRANSITION);
        }
        var participant = participants.findLockedByIdAndBookingId(command.participantId(), command.bookingId())
                .orElseThrow(BookingException::notFound);
        if (participant.getCheckedInAt() == null) {
            participant.setCheckedInAt(OffsetDateTime.now(clock));
            participant.setCheckedInBy(command.actorId());
            participant.setCheckInNote(command.note() == null ? null : command.note().strip());
        }
        return new ParticipantCheckInResponse(participant.getId(), command.bookingId(), participant.getCheckedInAt(),
                participant.getCheckedInBy(), participant.getCheckInNote());
    }

    @Override
    @Transactional
    public void requireCheckedIn(ParticipantCheckInCommand command) {
        var booking = bookings.findForUpdate(command.bookingId()).orElseThrow(BookingException::notFound);
        if (!booking.getScheduleId().equals(command.scheduleId())) {
            throw BookingException.notFound();
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.INVALID_BOOKING_TRANSITION);
        }
        var participant = participants.findLockedByIdAndBookingId(command.participantId(), command.bookingId())
                .orElseThrow(BookingException::notFound);
        if (participant.getCheckedInAt() == null) {
            throw new BookingException(BookingErrorCode.PARTICIPANT_NOT_CHECKED_IN);
        }
    }
}
