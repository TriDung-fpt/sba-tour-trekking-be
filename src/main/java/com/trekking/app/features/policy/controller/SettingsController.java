package com.trekking.app.features.policy.controller;

import com.trekking.app.features.policy.dto.request.SystemSettingCreateRequest;
import com.trekking.app.features.policy.dto.response.SystemSettingResponse;
import com.trekking.app.features.policy.service.SettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@SecurityRequirement(name = "bearerAuth")
public class SettingsController {
    private final SettingsService service;

    public SettingsController(SettingsService service) { this.service = service; }

    @GetMapping
    public List<SystemSettingResponse> current() { return service.current(); }

    @GetMapping("/{settingKey}/versions")
    public List<SystemSettingResponse> versions(@PathVariable String settingKey) {
        return service.versions(settingKey);
    }

    @PostMapping
    public SystemSettingResponse publish(@Valid @RequestBody SystemSettingCreateRequest request) {
        return service.publish(request);
    }
}
