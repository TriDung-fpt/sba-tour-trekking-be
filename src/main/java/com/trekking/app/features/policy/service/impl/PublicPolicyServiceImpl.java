package com.trekking.app.features.policy.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.policy.config.PublicPolicyProperties;
import com.trekking.app.features.policy.dto.response.PublicPolicyResponse;
import com.trekking.app.features.policy.repository.PolicyRepository;
import com.trekking.app.features.policy.repository.PolicyVersionRepository;
import com.trekking.app.features.policy.service.PublicPolicyService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicPolicyServiceImpl implements PublicPolicyService {
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final PublicPolicyProperties properties;
    private final Clock clock;

    public PublicPolicyServiceImpl(PolicyRepository policies, PolicyVersionRepository versions,
                                   PublicPolicyProperties properties, Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public PublicPolicyResponse read(String documentCode) {
        String policyCode = properties.policyCode(documentCode).orElseThrow(PublicPolicyServiceImpl::notFound);
        var policy = policies.findByCode(policyCode)
                .filter(value -> Boolean.TRUE.equals(value.getActive()) && value.getDeletedAt() == null)
                .orElseThrow(PublicPolicyServiceImpl::notFound);
        var now = OffsetDateTime.now(clock);
        // A cancelled scheduled version never displaces the current published version.
        // A revoked current version must not silently restore older terms.
        var version = versions.findAllByPolicyIdOrderByVersionNumberDesc(policy.getId()).stream()
                .filter(value -> value.getApprovedBy() != null && value.getApprovedAt() != null
                        && !value.getApprovedAt().isAfter(now) && !value.getEffectiveFrom().isAfter(now))
                .filter(value -> value.getRevocation() == null
                        || !value.getRevocation().getRevokedAt().isBefore(value.getEffectiveFrom()))
                .findFirst()
                .filter(value -> value.getRevocation() == null)
                .orElseThrow(PublicPolicyServiceImpl::notFound);
        return new PublicPolicyResponse(documentCode.toUpperCase(Locale.ROOT), policy.getTitle(),
                version.getContent(), version.getVersionNumber(), version.getEffectiveFrom());
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "PUBLIC_POLICY_NOT_FOUND", "Public document is not available");
    }
}
