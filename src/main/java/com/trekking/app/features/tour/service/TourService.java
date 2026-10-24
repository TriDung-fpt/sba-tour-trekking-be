package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.TourRequest;
import com.trekking.app.features.tour.dto.request.ScheduleRequest;
import com.trekking.app.features.tour.dto.response.TourResponse;
import com.trekking.app.features.tour.dto.response.ScheduleResponse;
import org.springframework.data.domain.Page;

public interface TourService {
    TourResponse create(TourRequest request);
    Page<TourResponse> list(int page, int size);
    TourResponse read(long tourId);
    TourResponse update(long tourId, TourRequest request);
    void delete(long tourId);
    ScheduleResponse createSchedule(long tourId, ScheduleRequest request);
    Page<ScheduleResponse> listSchedules(long tourId, int page, int size);
    ScheduleResponse readSchedule(long tourId, long scheduleId);
    ScheduleResponse updateSchedule(long tourId, long scheduleId, ScheduleRequest request);
    void deleteSchedule(long tourId, long scheduleId);
}
