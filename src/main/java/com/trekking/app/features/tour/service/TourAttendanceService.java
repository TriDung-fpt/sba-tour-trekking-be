package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.ParticipantCheckInRequest;
import com.trekking.app.features.tour.dto.request.ParticipantCheckOutRequest;
import com.trekking.app.features.tour.dto.response.ParticipantCheckInResponse;
import com.trekking.app.features.tour.dto.response.ParticipantCheckOutResponse;

public interface TourAttendanceService {
    ParticipantCheckInResponse checkIn(long scheduleId, long bookingId, long participantId,
                                       ParticipantCheckInRequest request);
    ParticipantCheckOutResponse checkOut(long scheduleId, long bookingId, long participantId,
                                         ParticipantCheckOutRequest request);
}
