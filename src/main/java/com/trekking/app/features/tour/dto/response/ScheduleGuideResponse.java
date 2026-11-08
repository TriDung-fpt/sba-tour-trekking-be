package com.trekking.app.features.tour.dto.response;

public record ScheduleGuideResponse(Long guideId, Long accountId, String fullName, String email, boolean isLead) {
}
