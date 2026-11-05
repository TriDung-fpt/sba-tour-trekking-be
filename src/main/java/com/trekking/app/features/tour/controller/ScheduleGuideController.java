package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import com.trekking.app.features.tour.dto.request.AssignScheduleGuidesRequest;
import com.trekking.app.features.tour.service.ScheduleGuideManagementService;
import com.trekking.app.features.tour.service.ScheduleGuideQueryService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules/{scheduleId}/guides")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleGuideController {
    private final ScheduleGuideQueryService guides;
    private final ScheduleGuideManagementService management;

    public ScheduleGuideController(ScheduleGuideQueryService guides, ScheduleGuideManagementService management) {
        this.guides = guides;
        this.management = management;
    }

    @GetMapping
    public List<ScheduleGuideSummaryResponse> list(@PathVariable long scheduleId) {
        return guides.listAssigned(scheduleId);
    }

    @PostMapping
    public List<ScheduleGuideSummaryResponse> replace(@PathVariable long scheduleId,
            @Valid @RequestBody AssignScheduleGuidesRequest request) {
        return management.replaceAssignments(scheduleId, request);
    }
}
