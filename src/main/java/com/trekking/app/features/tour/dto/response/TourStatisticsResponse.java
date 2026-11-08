package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record TourStatisticsResponse(long scheduleCount, List<Equipment> equipment) {
    public record Equipment(long equipmentId, String name, long quantity) {}
}
