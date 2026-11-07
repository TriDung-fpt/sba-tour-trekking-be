package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.response.GuideResponse;
import com.trekking.app.features.tour.dto.response.ScheduleGuideResponse;
import com.trekking.app.features.tour.service.GuideAssignmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class GuideAssignmentController {
    private final GuideAssignmentService guideAssignments;

    public GuideAssignmentController(GuideAssignmentService guideAssignments) {
        this.guideAssignments = guideAssignments;
    }

    @GetMapping("/api/v1/admin/guides")
    public List<GuideResponse> listAssignableGuides() {
        return guideAssignments.listAssignableGuides();
    }

    @GetMapping("/api/v1/schedules/{scheduleId}/guides")
    public List<ScheduleGuideResponse> listScheduleGuides(@PathVariable long scheduleId) {
        return guideAssignments.listScheduleGuides(scheduleId);
    }
}
