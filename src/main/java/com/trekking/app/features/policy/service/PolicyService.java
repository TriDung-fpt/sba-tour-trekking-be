package com.trekking.app.features.policy.service;

import com.trekking.app.features.policy.dto.request.PolicyCreateRequest;
import com.trekking.app.features.policy.dto.request.PolicyPublishRequest;
import com.trekking.app.features.policy.dto.response.PolicyResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionResponse;
import java.util.List;

public interface PolicyService {
    PolicyResponse create(PolicyCreateRequest request);
    PolicyVersionResponse publish(long policyId, PolicyPublishRequest request);
    List<PolicyResponse> list();
    PolicyResponse read(long policyId);
    List<PolicyVersionResponse> versions(long policyId);
}
