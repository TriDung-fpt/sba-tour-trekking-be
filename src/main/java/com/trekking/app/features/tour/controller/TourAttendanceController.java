package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.ParticipantCheckInRequest;
import com.trekking.app.features.tour.dto.request.ParticipantCheckOutRequest;
import com.trekking.app.features.tour.dto.response.ParticipantCheckInResponse;
import com.trekking.app.features.tour.dto.response.ParticipantCheckOutResponse;
import com.trekking.app.features.tour.service.TourAttendanceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules/{scheduleId}/bookings/{bookingId}/participants")
@SecurityRequirement(name = "bearerAuth")
public class TourAttendanceController {
    private final TourAttendanceService attendance;

    public TourAttendanceController(TourAttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/{participantId}/check-in")
    public ParticipantCheckInResponse checkIn(@PathVariable long scheduleId, @PathVariable long bookingId,
                                              @PathVariable long participantId,
                                              @Valid @RequestBody ParticipantCheckInRequest request) {
        return attendance.checkIn(scheduleId, bookingId, participantId, request);
    }

    @PostMapping("/{participantId}/check-out")
    public ParticipantCheckOutResponse checkOut(@PathVariable long scheduleId, @PathVariable long bookingId,
                                                @PathVariable long participantId,
                                                @Valid @RequestBody ParticipantCheckOutRequest request) {
        return attendance.checkOut(scheduleId, bookingId, participantId, request);
    }
}
