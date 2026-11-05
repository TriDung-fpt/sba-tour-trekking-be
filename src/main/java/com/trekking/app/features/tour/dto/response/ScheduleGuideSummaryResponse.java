package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record ScheduleGuideSummaryResponse(long guideId, String fullName, String phone, String email,
                                           boolean isLead, OffsetDateTime assignedAt) {}
