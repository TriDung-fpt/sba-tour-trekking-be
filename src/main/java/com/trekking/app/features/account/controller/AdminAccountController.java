package com.trekking.app.features.account.controller;

import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.dto.response.AdminAccountPageResponse;
import com.trekking.app.features.account.service.AccountAdministrationService;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountController {
    private final AccountAdministrationService service;

    public AdminAccountController(AccountAdministrationService service) { this.service = service; }

    @GetMapping
    public AdminAccountPageResponse list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String sort) {
        return service.list(page, size, q, role, status, sort);
    }

    @GetMapping("/{id}")
    public AdminAccountResponse get(@PathVariable Long id) { return service.get(id); }


    @PostMapping("/{id}/block")
    public AdminAccountResponse block(@PathVariable Long id,
                                       @Valid @RequestBody AccountAdministrationRequests.Reason request) {
        return service.block(id, request.reason());
    }

    @PostMapping("/{id}/unblock")
    public AdminAccountResponse unblock(@PathVariable Long id,
                                         @Valid @RequestBody AccountAdministrationRequests.Reason request) {
        return service.unblock(id, request.reason());
    }

    @PutMapping("/{id}/roles")
    public AdminAccountResponse replaceRoles(@PathVariable Long id,
                                             @Valid @RequestBody AccountAdministrationRequests.Roles request) {
        return service.replaceRoles(id, request);
    }
}
