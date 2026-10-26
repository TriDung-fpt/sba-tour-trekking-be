package com.trekking.app.features.account.controller;

import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.service.AccountAdministrationService;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
    public Page<AdminAccountResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String query,
                                           @RequestParam(required = false) String role,
                                           @RequestParam(required = false) String status) {
        return service.list(query, role, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminAccountResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public AdminAccountResponse update(@PathVariable Long id,
                                       @Valid @RequestBody AccountAdministrationRequests.Update request) {
        return service.update(id, request);
    }

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
