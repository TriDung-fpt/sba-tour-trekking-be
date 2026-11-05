package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.request.ParticipantCheckInCommand;
import com.trekking.app.features.booking.dto.response.ParticipantCheckInResponse;

public interface ParticipantCheckInService {
    ParticipantCheckInResponse checkIn(ParticipantCheckInCommand command);
    void requireCheckedIn(ParticipantCheckInCommand command);
}
