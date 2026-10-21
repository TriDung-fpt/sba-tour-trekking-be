package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.tour.service.TourWorkflowService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tours")
@PreAuthorize("hasRole('ADMIN')")
public class TourAdminController {
    private final TourWorkflowService workflow;
    public TourAdminController(TourWorkflowService workflow) { this.workflow = workflow; }
    @GetMapping("/review")
    public PagedResponse<ReviewQueueItem> queue(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = workflow.reviewQueue(page, size);
        return new PagedResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @PostMapping("/{tourId}/approve")
    public ActionMessage approve(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId) {
        return workflow.approve(user.accountId(), tourId);
    }
    @PostMapping("/{tourId}/reject")
    public ActionMessage reject(@AuthenticationPrincipal UserPrincipal user, @PathVariable long tourId,
            @Valid @RequestBody ModerateTour request) { return workflow.reject(user.accountId(), tourId, request.reason()); }
}
