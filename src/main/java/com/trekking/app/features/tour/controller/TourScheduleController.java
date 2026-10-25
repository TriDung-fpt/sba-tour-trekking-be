package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.CreateScheduleRequest;
import com.trekking.app.features.tour.dto.request.UpdateScheduleRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDetailResponse;
import com.trekking.app.features.tour.dto.response.ScheduleSummaryResponse;
import com.trekking.app.features.tour.service.TourService;
import com.trekking.app.features.tour.security.TourReadAccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours/{tourId}/schedules")
public class TourScheduleController {
    private final TourService tours;

    public TourScheduleController(TourService tours) {
        this.tours = tours;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public ScheduleDetailResponse create(@PathVariable long tourId, @Valid @RequestBody CreateScheduleRequest request) {
        return tours.createSchedule(tourId, request);
    }

    @GetMapping
    public Page<ScheduleSummaryResponse> list(@PathVariable long tourId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(defaultValue = "false") boolean upcomingOnly,
                                              Authentication authentication) {
        if (TourReadAccess.isPublic(authentication)) {
            return tours.listPublicUpcomingSchedules(tourId, page, size);
        }
        return upcomingOnly ? tours.listUpcomingSchedules(tourId, page, size) : tours.listSchedules(tourId, page, size);
    }

    @GetMapping("/{scheduleId}")
    public ScheduleDetailResponse read(@PathVariable long tourId, @PathVariable long scheduleId,
                                       Authentication authentication) {
        return TourReadAccess.isPublic(authentication)
                ? tours.readPublicSchedule(tourId, scheduleId)
                : tours.readSchedule(tourId, scheduleId);
    }

    @PutMapping("/{scheduleId}")
    @SecurityRequirement(name = "bearerAuth")
    public ScheduleDetailResponse update(@PathVariable long tourId, @PathVariable long scheduleId,
                                         @Valid @RequestBody UpdateScheduleRequest request) {
        return tours.updateSchedule(tourId, scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable long tourId, @PathVariable long scheduleId) {
        tours.deleteSchedule(tourId, scheduleId);
    }
}
