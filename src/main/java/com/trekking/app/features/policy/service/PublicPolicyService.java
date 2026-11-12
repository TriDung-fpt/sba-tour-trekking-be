package com.trekking.app.features.policy.service;

import com.trekking.app.features.policy.dto.response.PublicPolicyResponse;

public interface PublicPolicyService {
    PublicPolicyResponse read(String documentCode);
}
