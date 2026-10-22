package com.trekking.app.features.company.controller;

import com.trekking.app.features.company.dto.request.PolicyCreateRequest;
import com.trekking.app.features.company.dto.request.PolicyPublishRequest;
import com.trekking.app.features.company.dto.response.PolicyResponse;
import com.trekking.app.features.company.dto.response.PolicyVersionResponse;
import com.trekking.app.features.company.service.PolicyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/policies")
@SecurityRequirement(name = "bearerAuth")
public class PolicyController {
    private final PolicyService policies;

    public PolicyController(PolicyService policies) { this.policies = policies; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse create(@Valid @RequestBody PolicyCreateRequest request) { return policies.create(request); }

    @PostMapping("/{policyId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyVersionResponse publish(@PathVariable long policyId, @Valid @RequestBody PolicyPublishRequest request) {
        return policies.publish(policyId, request);
    }
}
