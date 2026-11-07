package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.TourStatisticsResponse;

public interface TourStatisticsService {
    TourStatisticsResponse read(long tourId);
}
