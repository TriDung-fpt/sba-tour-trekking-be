package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import java.util.List;

public interface ScheduleGuideQueryService {
    List<ScheduleGuideSummaryResponse> listAssigned(long scheduleId);
}
