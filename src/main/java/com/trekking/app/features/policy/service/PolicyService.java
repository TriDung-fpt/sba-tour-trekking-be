package com.trekking.app.features.policy.service;

import com.trekking.app.features.policy.dto.request.PolicyCreateRequest;
import com.trekking.app.features.policy.dto.request.PolicyPublishRequest;
import com.trekking.app.features.policy.dto.response.PolicyResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionResponse;
import java.util.List;

public interface PolicyService {
    void delete(long policyId);
    PolicyResponse createWithVersion(com.trekking.app.features.policy.dto.request.PolicyCreateWithVersionRequest request);
    PolicyResponse changeStatus(long policyId, com.trekking.app.features.policy.dto.request.PolicyStatusRequest request);
    PolicyVersionResponse revoke(long policyId, long versionId, com.trekking.app.features.policy.dto.request.PolicyRevokeRequest request);
    List<PolicyVersionResponse> effectiveCancellationVersions();
    List<PolicyVersionResponse> effectiveVersions();
    void requireEffectiveCancellationVersion(long versionId);
    PolicyResponse create(PolicyCreateRequest request);
    PolicyVersionResponse publish(long policyId, PolicyPublishRequest request);
    List<PolicyResponse> list();
    PolicyResponse read(long policyId);
    List<PolicyVersionResponse> versions(long policyId);
}
