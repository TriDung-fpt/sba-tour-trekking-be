package com.trekking.app.features.account.controller;

import com.trekking.app.features.account.dto.response.GuideOptionResponse;
import com.trekking.app.features.account.service.AccountAdministrationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/guides")
@SecurityRequirement(name = "bearerAuth")
public class AdminGuideController {
    private final AccountAdministrationService service;

    public AdminGuideController(AccountAdministrationService service) { this.service = service; }

    @GetMapping
    public List<GuideOptionResponse> list() { return service.listGuides(); }
}
