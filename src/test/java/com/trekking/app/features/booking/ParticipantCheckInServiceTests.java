package com.trekking.app.features.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.trekking.app.features.booking.dto.request.ParticipantCheckInCommand;
import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.booking.entity.Participant;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.exception.BookingException;
import com.trekking.app.features.booking.repository.BookingRepository;
import com.trekking.app.features.booking.repository.ParticipantRepository;
import com.trekking.app.features.booking.service.impl.ParticipantCheckInServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParticipantCheckInServiceTests {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ParticipantCheckInServiceImpl service = new ParticipantCheckInServiceImpl(bookings, participants, clock);

    @Test
    void recordsCheckInOnceForAssignedGuide() {
        var booking = new Booking();
        booking.setScheduleId(3L);
        booking.setStatus(BookingStatus.CONFIRMED);
        var participant = mock(Participant.class, CALLS_REAL_METHODS);
        when(participant.getId()).thenReturn(2L);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(participants.findLockedByIdAndBookingId(2L, 1L)).thenReturn(Optional.of(participant));
        var first = service.checkIn(new ParticipantCheckInCommand(3, 1, 2, 4, " present "));
        var repeat = service.checkIn(new ParticipantCheckInCommand(3, 1, 2, 4, "different"));

        assertThat(first).isEqualTo(repeat);
        assertThat(first.checkedInAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(first.checkedInBy()).isEqualTo(4);
        assertThat(first.note()).isEqualTo("present");
    }

    @Test
    void rejectsUnconfirmedBookings() {
        var booking = new Booking();
        booking.setScheduleId(3L);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.checkIn(new ParticipantCheckInCommand(3, 1, 2, 4, null)))
                .isInstanceOf(BookingException.class);
        verifyNoInteractions(participants);
    }

    @Test
    void rejectsBookingFromDifferentSchedule() {
        var booking = new Booking();
        booking.setScheduleId(99L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.checkIn(new ParticipantCheckInCommand(3, 1, 2, 4, null)))
                .isInstanceOf(BookingException.class);
        verifyNoInteractions(participants);
    }

    @Test
    void checkoutRequiresParticipantCheckIn() {
        var booking = new Booking();
        booking.setScheduleId(3L);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(participants.findLockedByIdAndBookingId(2L, 1L)).thenReturn(Optional.of(new Participant()));
        assertThatThrownBy(() -> service.requireCheckedIn(new ParticipantCheckInCommand(3, 1, 2, 4, null)))
                .isInstanceOf(BookingException.class)
                .extracting("code").isEqualTo("PARTICIPANT_NOT_CHECKED_IN");
    }
}
