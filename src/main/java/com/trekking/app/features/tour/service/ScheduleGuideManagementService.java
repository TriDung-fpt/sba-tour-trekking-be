package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.AssignScheduleGuidesRequest;
import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import java.util.List;

public interface ScheduleGuideManagementService {
    List<ScheduleGuideSummaryResponse> replaceAssignments(long scheduleId, AssignScheduleGuidesRequest request);
}
