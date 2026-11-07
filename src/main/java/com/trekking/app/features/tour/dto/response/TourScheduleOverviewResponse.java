package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.TourStatus;

public record TourScheduleOverviewResponse(long tourId, String title, TourStatus status, long scheduleCount) {}
