package com.trekking.app.features.booking.controller;

import com.trekking.app.features.booking.dto.response.ScheduleAvailabilityResponse;
import com.trekking.app.features.booking.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleAvailabilityController {
    private final BookingService bookings;

    public ScheduleAvailabilityController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/{scheduleId}/availability")
    public ScheduleAvailabilityResponse availability(@PathVariable long scheduleId) {
        return bookings.availability(scheduleId);
    }
}
