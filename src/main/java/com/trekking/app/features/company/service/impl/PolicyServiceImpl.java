package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.company.dto.request.PolicyCreateRequest;
import com.trekking.app.features.company.dto.request.PolicyPublishRequest;
import com.trekking.app.features.company.dto.response.PolicyResponse;
import com.trekking.app.features.company.dto.response.PolicyVersionResponse;
import com.trekking.app.features.company.entity.Policy;
import com.trekking.app.features.company.entity.PolicyVersion;
import com.trekking.app.features.company.enums.PolicyKind;
import com.trekking.app.features.company.repository.CompanyRepository;
import com.trekking.app.features.company.repository.PolicyRepository;
import com.trekking.app.features.company.repository.PolicyVersionRepository;
import com.trekking.app.features.company.service.PolicyService;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PolicyServiceImpl implements PolicyService {
    private final AuthorizationService authorization;
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final CompanyRepository companies;
    private final Clock clock;

    public PolicyServiceImpl(AuthorizationService authorization, PolicyRepository policies,
                             PolicyVersionRepository versions, CompanyRepository companies, Clock clock) {
        this.authorization = authorization;
        this.policies = policies;
        this.versions = versions;
        this.companies = companies;
        this.clock = clock;
    }

    @Override
    public PolicyResponse create(PolicyCreateRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        if (request.companyId() != null && !companies.existsById(request.companyId())) throw notFound();
        if (policies.existsByCode(request.code().trim())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_CODE_EXISTS", "Policy code already exists");
        }
        var policy = new Policy();
        policy.setCode(request.code().trim());
        policy.setTitle(request.title().trim());
        policy.setKind(PolicyKind.COOPERATION);
        policy.setCompanyId(request.companyId());
        policy.setCreatedBy(authorization.currentAccountId());
        policies.saveAndFlush(policy);
        return new PolicyResponse(policy.getId(), policy.getCode(), policy.getTitle(), policy.getKind().getValue(),
                policy.getCompanyId(), policy.getActive());
    }

    @Override
    public PolicyVersionResponse publish(long policyId, PolicyPublishRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = policies.findLockedById(policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (policy.getKind() != PolicyKind.COOPERATION || !Boolean.TRUE.equals(policy.getActive())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_NOT_PUBLISHABLE", "Policy must be active cooperation policy");
        }
        var version = new PolicyVersion();
        version.setPolicyId(policyId);
        version.setVersionNumber(versions.lastVersionNumber(policyId) + 1);
        version.setContent(request.content());
        version.setEffectiveFrom(request.effectiveFrom());
        version.setPlatformFeePercent(request.platformFeePercent());
        version.setPayoutWaitHours(request.payoutWaitHours());
        version.setPayoutTermsSnapshot(request.payoutTermsSnapshot());
        version.setApprovedBy(authorization.currentAccountId());
        version.setApprovedAt(OffsetDateTime.now(clock));
        versions.saveAndFlush(version);
        return response(policy, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> eligible(long companyId) {
        authorization.requireCompany(Permission.COMPANY_READ, companyId);
        var now = OffsetDateTime.now(clock);
        var result = new ArrayList<PolicyVersionResponse>();
        for (var policy : policies.findApplicable(PolicyKind.COOPERATION, companyId)) {
            versions.findFirstByPolicyIdAndApprovedByIsNotNullAndApprovedAtLessThanEqualAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(
                    policy.getId(), now, now).filter(PolicyServiceImpl::hasTerms)
                    .ifPresent(version -> result.add(response(policy, version)));
        }
        return List.copyOf(result);
    }

    static boolean hasTerms(PolicyVersion version) {
        return version.getPlatformFeePercent() != null && version.getPayoutWaitHours() != null
                && version.getPayoutTermsSnapshot() != null && !version.getPayoutTermsSnapshot().isBlank();
    }

    static PolicyVersionResponse response(Policy policy, PolicyVersion version) {
        return new PolicyVersionResponse(version.getId(), policy.getId(), policy.getCode(), policy.getTitle(),
                version.getVersionNumber(), version.getContent(), version.getEffectiveFrom(), version.getApprovedBy(),
                version.getApprovedAt(), version.getPlatformFeePercent(), version.getPayoutWaitHours(), version.getPayoutTermsSnapshot());
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }
}
