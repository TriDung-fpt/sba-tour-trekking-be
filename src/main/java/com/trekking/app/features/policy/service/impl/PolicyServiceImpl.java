package com.trekking.app.features.policy.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.policy.dto.request.PolicyCreateRequest;
import com.trekking.app.features.policy.dto.request.PolicyPublishRequest;
import com.trekking.app.features.policy.dto.response.PolicyResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionResponse;
import com.trekking.app.features.policy.entity.Policy;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.enums.PolicyKind;
import com.trekking.app.features.policy.repository.PolicyRepository;
import com.trekking.app.features.policy.repository.PolicyVersionRepository;
import com.trekking.app.features.policy.service.PolicyService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PolicyServiceImpl implements PolicyService {
    private static final List<PolicyKind> POLICY_KINDS = List.of(PolicyKind.CANCELLATION, PolicyKind.PAYMENT, PolicyKind.GENERAL);
    private final AuthorizationService authorization;
    private final PolicyRepository policies;
    private final PolicyVersionRepository versions;
    private final AuditLogRepository audits;
    private final Clock clock;
    private final ObjectMapper json;

    public PolicyServiceImpl(AuthorizationService authorization, PolicyRepository policies,
                             PolicyVersionRepository versions, AuditLogRepository audits, Clock clock, ObjectMapper json) {
        this.authorization = authorization;
        this.policies = policies;
        this.versions = versions;
        this.audits = audits;
        this.clock = clock;
        this.json = json;
    }

    @Override
    public PolicyResponse create(PolicyCreateRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var kind = POLICY_KINDS.stream().filter(candidate -> candidate.getValue().equals(request.kind())).findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_POLICY_KIND", "Unsupported customer policy kind"));
        if (policies.existsByCode(request.code().trim())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_CODE_EXISTS", "Policy code already exists");
        }
        var policy = new Policy();
        policy.setCode(request.code().trim());
        policy.setTitle(request.title().trim());
        policy.setKind(kind);
        policy.setCreatedBy(authorization.currentAccountId());
        policies.saveAndFlush(policy);
        audit(policy.getId(), "POLICY_CREATED", Map.of("code", policy.getCode(), "kind", kind.getValue()));
        return response(policy);
    }

    @Override
    public PolicyVersionResponse publish(long policyId, PolicyPublishRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = policies.findLockedById(policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_NOT_PUBLISHABLE", "Only active customer policies can be published");
        }
        // PostgreSQL stores microseconds. Compare the same precision that will be persisted.
        var effectiveFrom = request.effectiveFrom().truncatedTo(ChronoUnit.MICROS);
        var previous = versions.findFirstByPolicyIdOrderByVersionNumberDesc(policyId);
        if (previous.isPresent() && !effectiveFrom.isAfter(previous.get().getEffectiveFrom())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_EFFECTIVE_FROM_OUT_OF_ORDER",
                    "A new policy version must become effective after the previous version");
        }
        var version = new PolicyVersion();
        version.setPolicyId(policyId);
        version.setVersionNumber(previous.map(value -> value.getVersionNumber() + 1).orElse(1));
        version.setContent(request.content());
        version.setEffectiveFrom(effectiveFrom);
        version.setApprovedBy(authorization.currentAccountId());
        version.setApprovedAt(OffsetDateTime.now(clock));
        versions.saveAndFlush(version);
        audit(policyId, "POLICY_VERSION_PUBLISHED", Map.of("versionId", version.getId(),
                "versionNumber", version.getVersionNumber(), "effectiveFrom", version.getEffectiveFrom().toString()));
        return response(policy, version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        authorization.require(Permission.POLICY_MANAGE);
        return policies.findAllByOrderByIdAsc().stream()
                .map(PolicyServiceImpl::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyResponse read(long policyId) {
        authorization.require(Permission.POLICY_MANAGE);
        return response(requirePolicy(policyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> versions(long policyId) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = requirePolicy(policyId);
        return versions.findAllByPolicyIdOrderByVersionNumberDesc(policyId).stream().map(value -> response(policy, value)).toList();
    }

    private Policy requirePolicy(long id) {
        return policies.findById(id).orElseThrow(PolicyServiceImpl::notFound);
    }

    private static PolicyResponse response(Policy policy) {
        return new PolicyResponse(policy.getId(), policy.getCode(), policy.getTitle(), policy.getKind().getValue(), policy.getActive());
    }

    private static PolicyVersionResponse response(Policy policy, PolicyVersion version) {
        return new PolicyVersionResponse(version.getId(), policy.getId(), policy.getCode(), policy.getTitle(),
                version.getVersionNumber(), version.getContent(), version.getEffectiveFrom(), version.getApprovedBy(), version.getApprovedAt());
    }

    private void audit(long id, String action, Map<String, Object> details) {
        var audit = new AuditLog();
        audit.setActorId(authorization.currentAccountId());
        audit.setEntityType("policy");
        audit.setEntityId(id);
        audit.setAction(action);
        audit.setDetails(json.writeValueAsString(details));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        audits.saveAndFlush(audit);
    }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Policy was not found");
    }
}
