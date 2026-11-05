package com.trekking.app.features.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.booking.dto.request.ParticipantCheckInCommand;
import com.trekking.app.features.booking.dto.response.ParticipantCheckInResponse;
import com.trekking.app.features.booking.service.ParticipantCheckInService;
import com.trekking.app.features.tour.dto.request.ParticipantCheckInRequest;
import com.trekking.app.features.tour.dto.request.ParticipantCheckOutRequest;
import com.trekking.app.features.tour.entity.TourParticipantCheckout;
import com.trekking.app.features.tour.repository.TourParticipantCheckoutRepository;
import com.trekking.app.features.tour.service.impl.TourAttendanceServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TourAttendanceServiceTests {
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final ParticipantCheckInService participants = mock(ParticipantCheckInService.class);
    private final TourParticipantCheckoutRepository checkouts = mock(TourParticipantCheckoutRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final TourAttendanceServiceImpl service = new TourAttendanceServiceImpl(authorization, participants, checkouts, clock);

    @Test
    void assignedGuideDelegatesCheckInToBooking() {
        var now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        when(authorization.currentAccountId()).thenReturn(4L);
        var command = new ParticipantCheckInCommand(3, 1, 2, 4, "present");
        when(participants.checkIn(command)).thenReturn(new ParticipantCheckInResponse(2, 1, now, 4, "present"));

        var result = service.checkIn(3, 1, 2, new ParticipantCheckInRequest("present"));

        verify(authorization).requireGuideSchedule(3);
        assertThat(result.checkedInBy()).isEqualTo(4);
        assertThat(result.checkedInAt()).isEqualTo(now);
    }

    @Test
    void unassignedGuideCannotReachBookingService() {
        doThrow(new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Not found"))
                .when(authorization).requireGuideSchedule(3);
        assertThatThrownBy(() -> service.checkIn(3, 1, 2, new ParticipantCheckInRequest(null)))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(participants);
    }

    @Test
    void assignedGuideRecordsOneCheckoutAndRetryReturnsOriginal() {
        when(authorization.currentAccountId()).thenReturn(4L);
        when(checkouts.save(any(TourParticipantCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(checkouts.findById(2L)).thenReturn(Optional.empty()).thenAnswer(invocation -> {
            var existing = new TourParticipantCheckout();
            existing.setParticipantId(2L);
            existing.setCheckedOutBy(4L);
            existing.setCheckedOutAt(OffsetDateTime.now(clock));
            existing.setNote("done");
            return Optional.of(existing);
        });

        var first = service.checkOut(3, 1, 2, new ParticipantCheckOutRequest(" done "));
        var retry = service.checkOut(3, 1, 2, new ParticipantCheckOutRequest("different"));

        assertThat(first).isEqualTo(retry);
        assertThat(first.checkedOutBy()).isEqualTo(4);
        verify(checkouts, times(1)).save(any(TourParticipantCheckout.class));
        verify(participants, times(2)).requireCheckedIn(any(ParticipantCheckInCommand.class));
    }

    @Test
    void unassignedGuideCannotCheckout() {
        doThrow(new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Not found"))
                .when(authorization).requireGuideSchedule(3);
        assertThatThrownBy(() -> service.checkOut(3, 1, 2, new ParticipantCheckOutRequest(null)))
                .isInstanceOf(AuthException.class);
        verifyNoInteractions(participants, checkouts);
    }
}
