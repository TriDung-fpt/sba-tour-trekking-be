package com.trekking.app.features.tour.service.impl;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.tour.dto.response.ScheduleGuideSummaryResponse;
import com.trekking.app.features.tour.repository.ScheduleGuideRepository;
import com.trekking.app.features.tour.repository.TourScheduleRepository;
import com.trekking.app.features.tour.service.ScheduleGuideQueryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleGuideQueryServiceImpl implements ScheduleGuideQueryService {
    private final ScheduleGuideRepository assignments;
    private final TourScheduleRepository schedules;
    private final AuthorizationService authorization;

    public ScheduleGuideQueryServiceImpl(ScheduleGuideRepository assignments, TourScheduleRepository schedules,
                                         AuthorizationService authorization) {
        this.assignments = assignments;
        this.schedules = schedules;
        this.authorization = authorization;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleGuideSummaryResponse> listAssigned(long scheduleId) {
        var roles = authorization.currentRoles();
        if (roles.contains(RoleType.ADMIN)) {
            authorization.require(Permission.GUIDE_MANAGE);
        } else if (roles.contains(RoleType.GUIDE)) {
            authorization.requireGuideSchedule(scheduleId);
        } else {
            authorization.require(Permission.GUIDE_MANAGE);
        }

        if (!schedules.existsById(scheduleId)) {
            throw new com.trekking.app.features.tour.exception.TourException(HttpStatus.NOT_FOUND,
                    "SCHEDULE_NOT_FOUND", "Schedule was not found");
        }
        return assignments.findDetailsByScheduleId(scheduleId).stream().map(assignment -> {
            var account = assignment.getGuide().getAccount();
            return new ScheduleGuideSummaryResponse(assignment.getGuideId(), account.getId(), account.getFullName(), account.getPhone(),
                    account.getEmail(), Boolean.TRUE.equals(assignment.getIsLead()), assignment.getCreatedAt());
        }).toList();
    }
}
