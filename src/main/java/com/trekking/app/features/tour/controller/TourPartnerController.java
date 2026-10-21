package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.tour.service.TourWorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/partner/tours")
@PreAuthorize("isAuthenticated()")
public class TourPartnerController {
    private final TourWorkflowService workflow;
    public TourPartnerController(TourWorkflowService workflow) { this.workflow = workflow; }

    @GetMapping
    public PagedResponse<TourItem> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        var result = workflow.partnerTours(user.accountId(), page, size, status);
        return new PagedResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @GetMapping("/policy-versions")
    public PolicyVersionList policyVersions(
            @AuthenticationPrincipal UserPrincipal user) {
        return new PolicyVersionList(workflow.cancellationPolicyVersions(user.accountId()));
    }
    @PostMapping
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public TourDetail create(@AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody TourWriteRequest request) { return workflow.createTour(user.accountId(), request); }
    @GetMapping("/{tourId}")
    @PreAuthorize("isAuthenticated()")
    public TourDetail get(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId) {
        return workflow.getPartnerTour(user.accountId(), tourId);
    }
    @PatchMapping("/{tourId}")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public TourDetail patch(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @Valid @RequestBody TourPatchRequest request) { return workflow.patchTour(user.accountId(), tourId, request); }
    @DeleteMapping("/{tourId}")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public ActionMessage archive(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId) {
        return workflow.archiveTour(user.accountId(), tourId);
    }
    @PostMapping("/{tourId}/restore")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public ActionMessage restore(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId) {
        return workflow.restoreTour(user.accountId(), tourId);
    }
    @PostMapping("/{tourId}/submit")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public ActionMessage submit(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId) {
        return workflow.submitTour(user.accountId(), tourId);
    }
    @PutMapping("/{tourId}/equipment-packages")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public TourManagementEquipment equipment(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @Valid @RequestBody EquipmentPackage request) { return workflow.putEquipmentPackage(user.accountId(), tourId, request); }

    @PostMapping("/{tourId}/schedules")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public TourManagementSchedule createSchedule(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @Valid @RequestBody ScheduleCreate request) { return workflow.createSchedule(user.accountId(), tourId, request); }
    @PatchMapping("/{tourId}/schedules/{scheduleId}")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public TourManagementSchedule patchSchedule(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @PathVariable long scheduleId, @Valid @RequestBody SchedulePatch request) {
        return workflow.patchSchedule(user.accountId(), tourId, scheduleId, request);
    }
    @PostMapping("/{tourId}/schedules/{scheduleId}/cancel")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public ActionMessage cancel(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @PathVariable long scheduleId, @Valid @RequestBody CancelSchedule request) {
        return workflow.cancelSchedule(user.accountId(), tourId, scheduleId, request);
    }
    @PostMapping("/{tourId}/schedules/{scheduleId}/complete")
    public ActionMessage complete(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @PathVariable long scheduleId) { return workflow.completeSchedule(user.accountId(), tourId, scheduleId); }

    @GetMapping("/{tourId}/schedules/{scheduleId}/roster")
    public RosterResponse roster(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @PathVariable long scheduleId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return workflow.roster(user.accountId(), tourId, scheduleId, page, size);
    }
    @GetMapping(value = "/{tourId}/schedules/{scheduleId}/roster.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> rosterXlsx(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @PathVariable long scheduleId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule-roster.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(workflow.rosterXlsx(user.accountId(), tourId, scheduleId));
    }
}
