package com.trekking.app.features.policy.controller;

import com.trekking.app.features.policy.dto.request.PolicyCreateRequest;
import com.trekking.app.features.policy.dto.request.PolicyPublishRequest;
import com.trekking.app.features.policy.dto.response.PolicyResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionResponse;
import com.trekking.app.features.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final com.trekking.app.features.policy.service.PolicyDocumentService documents;

    public PolicyController(PolicyService policies, com.trekking.app.features.policy.service.PolicyDocumentService documents) { this.policies = policies; this.documents = documents; }

    @org.springframework.web.bind.annotation.DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long policyId) { policies.delete(policyId); }

    @PostMapping(value = "/extract-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public com.trekking.app.features.policy.dto.response.PolicyDocumentResponse extractDocument(
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return documents.extract(file);
    }

    @PostMapping("/with-version")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse createWithVersion(@Valid @RequestBody com.trekking.app.features.policy.dto.request.PolicyCreateWithVersionRequest request) {
        return policies.createWithVersion(request);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{policyId}/status")
    public PolicyResponse changeStatus(@PathVariable long policyId, @Valid @RequestBody com.trekking.app.features.policy.dto.request.PolicyStatusRequest request) {
        return policies.changeStatus(policyId, request);
    }

    @PostMapping("/{policyId}/versions/{versionId}/revoke")
    public PolicyVersionResponse revoke(@PathVariable long policyId, @PathVariable long versionId,
                                         @Valid @RequestBody com.trekking.app.features.policy.dto.request.PolicyRevokeRequest request) {
        return policies.revoke(policyId, versionId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse create(@Valid @RequestBody PolicyCreateRequest request) { return policies.create(request); }

    @GetMapping
    public List<PolicyResponse> list() { return policies.list(); }

    @GetMapping("/cancellation/available")
    public List<PolicyVersionResponse> availableCancellationVersions() {
        return policies.effectiveCancellationVersions();
    }

    @GetMapping("/available")
    public List<PolicyVersionResponse> availableVersions() { return policies.effectiveVersions(); }

    @GetMapping("/{policyId}")
    public PolicyResponse read(@PathVariable long policyId) { return policies.read(policyId); }

    @PostMapping("/{policyId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyVersionResponse publish(@PathVariable long policyId, @Valid @RequestBody PolicyPublishRequest request) {
        return policies.publish(policyId, request);
    }

    @GetMapping("/{policyId}/versions")
    public List<PolicyVersionResponse> versions(@PathVariable long policyId) { return policies.versions(policyId); }
}
