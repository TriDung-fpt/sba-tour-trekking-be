package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.TourStatisticsResponse;
import com.trekking.app.features.tour.dto.response.TourScheduleOverviewResponse;
import org.springframework.data.domain.Page;

public interface TourStatisticsService {
    TourStatisticsResponse read(long tourId);
    Page<TourScheduleOverviewResponse> scheduleOverview(String query, int page, int size);
}
