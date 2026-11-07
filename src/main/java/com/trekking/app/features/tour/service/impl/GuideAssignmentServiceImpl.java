package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.repository.GuideRepository;
import com.trekking.app.features.tour.dto.response.GuideResponse;
import com.trekking.app.features.tour.dto.response.ScheduleGuideResponse;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.GuideAssignmentService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GuideAssignmentServiceImpl implements GuideAssignmentService {
    private final GuideRepository guides;
    private final ScheduleGuideRepository assignments;
    private final TourScheduleRepository schedules;
    private final AuthorizationService authorization;

    public GuideAssignmentServiceImpl(GuideRepository guides, ScheduleGuideRepository assignments,
                                      TourScheduleRepository schedules, AuthorizationService authorization) {
        this.guides = guides;
        this.assignments = assignments;
        this.schedules = schedules;
        this.authorization = authorization;
    }

    @Override
    public List<GuideResponse> listAssignableGuides() {
        authorization.require(Permission.GUIDE_MANAGE);
        return guides.findActiveGuides().stream()
                .map(guide -> new GuideResponse(guide.getId(), guide.getAccountId(),
                        guide.getAccount().getFullName(), guide.getAccount().getEmail()))
                .toList();
    }

    @Override
    public List<ScheduleGuideResponse> listScheduleGuides(long scheduleId) {
        authorization.require(Permission.GUIDE_MANAGE);
        if (!schedules.existsById(scheduleId)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
        }
        return assignments.findDetailsByScheduleId(scheduleId).stream()
                .map(assignment -> new ScheduleGuideResponse(assignment.getGuideId(),
                        assignment.getGuide().getAccountId(), assignment.getGuide().getAccount().getFullName(),
                        assignment.getGuide().getAccount().getEmail(), Boolean.TRUE.equals(assignment.getIsLead())))
                .toList();
    }
}
