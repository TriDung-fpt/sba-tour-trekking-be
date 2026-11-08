package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.GuideResponse;
import com.trekking.app.features.tour.dto.response.ScheduleGuideResponse;
import java.util.List;

public interface GuideAssignmentService {
    List<GuideResponse> listAssignableGuides();
    List<ScheduleGuideResponse> listScheduleGuides(long scheduleId);
}
