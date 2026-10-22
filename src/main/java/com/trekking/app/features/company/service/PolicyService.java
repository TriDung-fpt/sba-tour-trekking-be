package com.trekking.app.features.company.service;

import com.trekking.app.features.company.dto.request.PolicyCreateRequest;
import com.trekking.app.features.company.dto.request.PolicyPublishRequest;
import com.trekking.app.features.company.dto.response.PolicyResponse;
import com.trekking.app.features.company.dto.response.PolicyVersionResponse;
import java.util.List;

public interface PolicyService {
    PolicyResponse create(PolicyCreateRequest request);
    PolicyVersionResponse publish(long policyId, PolicyPublishRequest request);
    List<PolicyVersionResponse> eligible(long companyId);
}
