package com.trekking.app.features.company.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.company.dto.request.RejectCompanyApplicationRequest;
import com.trekking.app.features.company.dto.response.CompanyApplicationQueueItem;
import com.trekking.app.features.company.dto.response.CompanyApplicationStatusResponse;
import com.trekking.app.features.company.service.CompanyApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/partner-applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompanyApplicationController {
    private final CompanyApplicationService applications;

    public AdminCompanyApplicationController(CompanyApplicationService applications) { this.applications = applications; }

    @GetMapping
    public List<CompanyApplicationQueueItem> pending() { return applications.pendingApplications(); }

    @PostMapping("/{companyId}/approve")
    public CompanyApplicationStatusResponse approve(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable long companyId) {
        return applications.approve(principal.accountId(), companyId);
    }

    @PostMapping("/{companyId}/reject")
    public CompanyApplicationStatusResponse reject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable long companyId, @Valid @RequestBody RejectCompanyApplicationRequest request) {
        return applications.reject(principal.accountId(), companyId, request);
    }
}
