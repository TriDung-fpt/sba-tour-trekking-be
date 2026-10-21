package com.trekking.app.features.statement.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.statement.dto.response.PartnerStatementPage;
import com.trekking.app.features.statement.service.PartnerStatementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner/statements")
@PreAuthorize("hasRole('COMPANY_MANAGER')")
public class PartnerStatementController {
    private final PartnerStatementService service;
    public PartnerStatementController(PartnerStatementService service) { this.service = service; }

    @GetMapping
    public PartnerStatementPage list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(user.accountId(), page, size);
    }
}
