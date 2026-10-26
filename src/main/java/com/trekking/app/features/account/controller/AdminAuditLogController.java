package com.trekking.app.features.account.controller;

import com.trekking.app.features.account.dto.response.AuditLogResponse;
import com.trekking.app.features.account.service.AccountAdministrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {
    private final AccountAdministrationService service;

    public AdminAuditLogController(AccountAdministrationService service) { this.service = service; }

    @GetMapping
    public Page<AuditLogResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String entityType,
                                       @RequestParam(required = false) Long entityId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) Long actorId) {
        return service.listAuditLogs(entityType, entityId, action, actorId, page, size);
    }
}
