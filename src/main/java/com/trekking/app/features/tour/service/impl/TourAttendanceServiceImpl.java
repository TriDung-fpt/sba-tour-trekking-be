package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.booking.dto.request.ParticipantCheckInCommand;
import com.trekking.app.features.booking.service.ParticipantCheckInService;
import com.trekking.app.features.tour.dto.request.ParticipantCheckInRequest;
import com.trekking.app.features.tour.dto.request.ParticipantCheckOutRequest;
import com.trekking.app.features.tour.dto.response.ParticipantCheckInResponse;
import com.trekking.app.features.tour.dto.response.ParticipantCheckOutResponse;
import com.trekking.app.features.tour.entity.TourParticipantCheckout;
import com.trekking.app.features.tour.repository.TourParticipantCheckoutRepository;
import com.trekking.app.features.tour.service.TourAttendanceService;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourAttendanceServiceImpl implements TourAttendanceService {
    private final AuthorizationService authorization;
    private final ParticipantCheckInService participants;
    private final TourParticipantCheckoutRepository checkouts;
    private final Clock clock;

    public TourAttendanceServiceImpl(AuthorizationService authorization, ParticipantCheckInService participants,
                                     TourParticipantCheckoutRepository checkouts, Clock clock) {
        this.authorization = authorization;
        this.participants = participants;
        this.checkouts = checkouts;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ParticipantCheckInResponse checkIn(long scheduleId, long bookingId, long participantId,
                                              ParticipantCheckInRequest request) {
        authorization.requireGuideSchedule(scheduleId);
        var result = participants.checkIn(new ParticipantCheckInCommand(scheduleId, bookingId, participantId,
                authorization.currentAccountId(), request.note()));
        return new ParticipantCheckInResponse(result.participantId(), result.bookingId(), result.checkedInAt(),
                result.checkedInBy(), result.note());
    }

    @Override
    @Transactional
    public ParticipantCheckOutResponse checkOut(long scheduleId, long bookingId, long participantId,
                                                ParticipantCheckOutRequest request) {
        authorization.requireGuideSchedule(scheduleId);
        long actorId = authorization.currentAccountId();
        participants.requireCheckedIn(new ParticipantCheckInCommand(scheduleId, bookingId, participantId, actorId,
                request.note()));
        var checkout = checkouts.findById(participantId).orElseGet(() -> {
            var record = new TourParticipantCheckout();
            record.setParticipantId(participantId);
            record.setCheckedOutAt(OffsetDateTime.now(clock));
            record.setCheckedOutBy(actorId);
            record.setNote(request.note() == null ? null : request.note().strip());
            return checkouts.save(record);
        });
        return new ParticipantCheckOutResponse(participantId, bookingId, checkout.getCheckedOutAt(),
                checkout.getCheckedOutBy(), checkout.getNote());
    }
}
