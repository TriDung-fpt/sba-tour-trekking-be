package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.tour.dto.request.AssignScheduleGuidesRequest;
import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import com.trekking.app.features.tour.entity.ScheduleGuide;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.ScheduleGuideManagementService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleGuideManagementServiceImpl implements ScheduleGuideManagementService {
    private final ScheduleGuideRepository assignments;
    private final TourScheduleRepository schedules;
    private final GuideRepository guides;
    private final AuthorizationService authorization;

    public ScheduleGuideManagementServiceImpl(ScheduleGuideRepository assignments, TourScheduleRepository schedules,
                                               GuideRepository guides, AuthorizationService authorization) {
        this.assignments = assignments;
        this.schedules = schedules;
        this.guides = guides;
        this.authorization = authorization;
    }

    @Override
    @Transactional
    public List<ScheduleGuideSummaryResponse> replaceAssignments(long scheduleId, AssignScheduleGuidesRequest request) {
        authorization.require(Permission.GUIDE_MANAGE);
        var schedule = schedules.findById(scheduleId).orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND,
                "SCHEDULE_NOT_FOUND", "Schedule was not found"));
        var requested = request.guides();
        var guideIds = requested.stream().map(AssignScheduleGuidesRequest.GuideAssignment::guideId).toList();
        if (new HashSet<>(guideIds).size() != guideIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "DUPLICATE_GUIDE", "A guide can only be assigned once");
        }
        long leadCount = requested.stream().filter(guide -> Boolean.TRUE.equals(guide.isLead())).count();
        if (!requested.isEmpty() && leadCount != 1) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_LEAD_GUIDE",
                    "Exactly one lead guide must be selected when guides are assigned",
                    Map.of("guides", "Select exactly one lead guide"));
        }

        var activeGuideIds = guides.findActiveGuides().stream().map(guide -> guide.getId()).collect(Collectors.toSet());
        for (var guideId : guideIds) {
            if (!activeGuideIds.contains(guideId)) {
                throw new TourException(HttpStatus.BAD_REQUEST, "GUIDE_NOT_ASSIGNABLE",
                        "Guide is missing, inactive, or does not have the GUIDE role",
                        Map.of("guides", "Choose an active guide"));
            }
            if (assignments.hasOverlappingAssignment(guideId, scheduleId, schedule.getStartsAt(), schedule.getEndsAt())) {
                throw new TourException(HttpStatus.CONFLICT, "GUIDE_SCHEDULE_CONFLICT",
                        "Guide is already assigned to an overlapping schedule",
                        Map.of("guideId", guideId.toString()));
            }
        }

        assignments.deleteAllByScheduleId(scheduleId);
        assignments.flush();
        for (var guide : requested) {
            var assignment = new ScheduleGuide();
            assignment.setScheduleId(scheduleId);
            assignment.setGuideId(guide.guideId());
            assignment.setIsLead(guide.isLead());
            assignment.setSchedule(schedule);
            assignment.setGuide(guides.getReferenceById(guide.guideId()));
            assignments.save(assignment);
        }
        assignments.flush();
        return assignments.findDetailsByScheduleId(scheduleId).stream().map(assignment -> {
            var account = assignment.getGuide().getAccount();
            return new ScheduleGuideSummaryResponse(assignment.getGuideId(), account.getId(), account.getFullName(), account.getPhone(),
                    account.getEmail(), Boolean.TRUE.equals(assignment.getIsLead()), assignment.getCreatedAt());
        }).toList();
    }
}
