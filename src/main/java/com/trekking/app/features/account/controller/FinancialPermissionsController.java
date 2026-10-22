package com.trekking.app.features.account.controller;

import com.trekking.app.features.account.dto.request.FinancialPermissionsRequest;
import com.trekking.app.features.account.dto.response.FinancialPermissionsResponse;
import com.trekking.app.features.account.service.FinancialPermissionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts/{accountId}/financial-permissions")
@SecurityRequirement(name = "bearerAuth")
public class FinancialPermissionsController {
    private final FinancialPermissionService service;

    public FinancialPermissionsController(FinancialPermissionService service) { this.service = service; }

    @GetMapping
    public FinancialPermissionsResponse get(@PathVariable long accountId) { return service.get(accountId); }

    @PutMapping
    public FinancialPermissionsResponse replace(@PathVariable long accountId,
                                                @Valid @RequestBody FinancialPermissionsRequest request) {
        return service.replace(accountId, request);
    }
}
