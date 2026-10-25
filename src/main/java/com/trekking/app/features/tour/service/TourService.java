package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.CreateScheduleRequest;
import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateScheduleRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDetailResponse;
import com.trekking.app.features.tour.dto.response.ScheduleSummaryResponse;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import org.springframework.data.domain.Page;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;

public interface TourService {
    TourDetailResponse create(CreateTourRequest request);
    Page<TourSummaryResponse> list(int page, int size);
    Page<TourSummaryResponse> list(String query, DifficultyLevel difficulty, Integer durationDays, TourStatus status, int page, int size);
    TourDetailResponse read(long tourId);
    TourDetailResponse update(long tourId, UpdateTourRequest request);
    void delete(long tourId);
    ScheduleDetailResponse createSchedule(long tourId, CreateScheduleRequest request);
    Page<ScheduleSummaryResponse> listSchedules(long tourId, int page, int size);
    Page<ScheduleSummaryResponse> listUpcomingSchedules(long tourId, int page, int size);
    ScheduleDetailResponse readSchedule(long tourId, long scheduleId);
    ScheduleDetailResponse updateSchedule(long tourId, long scheduleId, UpdateScheduleRequest request);
    void deleteSchedule(long tourId, long scheduleId);
}
