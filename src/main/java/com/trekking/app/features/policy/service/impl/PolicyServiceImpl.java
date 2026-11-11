package com.trekking.app.features.policy.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.policy.dto.request.PolicyCreateRequest;
import com.trekking.app.features.policy.dto.request.PolicyPublishRequest;
import com.trekking.app.features.policy.dto.request.RefundTierRequest;
import com.trekking.app.features.policy.dto.response.PolicyResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionResponse;
import com.trekking.app.features.policy.dto.response.PolicyVersionSummary;
import com.trekking.app.features.policy.entity.Policy;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.enums.PolicyKind;
import com.trekking.app.features.policy.repository.PolicyRepository;
import com.trekking.app.features.policy.repository.PolicyVersionRepository;
import com.trekking.app.features.policy.service.PolicyService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    private final com.trekking.app.features.policy.repository.PolicyVersionRevocationRepository revocations;

    public PolicyServiceImpl(AuthorizationService authorization, PolicyRepository policies,
                             PolicyVersionRepository versions, AuditLogRepository audits, Clock clock, ObjectMapper json,
                             com.trekking.app.features.policy.repository.PolicyVersionRevocationRepository revocations) {
        this.authorization = authorization;
        this.policies = policies;
        this.versions = versions;
        this.audits = audits;
        this.clock = clock;
        this.json = json;
        this.revocations = revocations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> effectiveCancellationVersions() {
        return effectiveVersions().stream().filter(v -> v.kind().equals("cancellation")).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionResponse> effectiveVersions() {
        authorization.require(Permission.POLICY_MANAGE);
        var now = OffsetDateTime.now(clock);
        var candidates = policies.findAllByOrderByIdAsc().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()) && p.getDeletedAt() == null)
                .flatMap(p -> versions.findAllByPolicyIdOrderByVersionNumberDesc(p.getId()).stream()
                        .filter(v -> v.getApprovedAt() != null && !v.getApprovedAt().isAfter(now) && !v.getEffectiveFrom().isAfter(now))
                        .filter(v -> v.getRevocation() == null || !v.getRevocation().getRevokedAt().isBefore(v.getEffectiveFrom()))
                        .findFirst().stream()
                        .filter(v -> v.getRevocation() == null).map(v -> response(p, v)))
                .toList();
        return candidates;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireEffectiveCancellationVersion(long versionId) {
        authorization.require(Permission.POLICY_MANAGE);
        var now = OffsetDateTime.now(clock);
        var version = versions.findById(versionId).orElseThrow(PolicyServiceImpl::notFound);
        if (!Boolean.TRUE.equals(version.getPolicy().getActive()) || version.getPolicy().getKind() != PolicyKind.CANCELLATION
                || version.getApprovedAt() == null || version.getApprovedAt().isAfter(now)
                || version.getEffectiveFrom().isAfter(now) || version.getRevocation() != null) {
            throw new AuthException(HttpStatus.CONFLICT, "CANCELLATION_POLICY_UNAVAILABLE",
                    "Select an active cancellation policy with an effective approved version");
        }
    }

    @Override
    public PolicyResponse createWithVersion(com.trekking.app.features.policy.dto.request.PolicyCreateWithVersionRequest request) {
        var policy = create(request.policy());
        publish(policy.id(), request.version());
        return policy;
    }

    @Override
    public PolicyResponse changeStatus(long policyId, com.trekking.app.features.policy.dto.request.PolicyStatusRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = policies.findLockedById(policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (policy.getDeletedAt() != null) throw notFound();
        policy.setActive(request.active());
        policies.saveAndFlush(policy);
        audit(policyId, request.active() ? "POLICY_ENABLED" : "POLICY_DISABLED", Map.of("reason", request.reason().trim()));
        return response(policy);
    }

    @Override
    public void delete(long policyId) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = policies.findLockedById(policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (policy.getDeletedAt() != null) throw notFound();
        policy.setActive(false);
        policy.setDeletedAt(OffsetDateTime.now(clock));
        policies.saveAndFlush(policy);
        audit(policyId, "POLICY_DELETED", Map.of("code", policy.getCode()));
    }

    @Override
    public PolicyVersionResponse revoke(long policyId, long versionId, com.trekking.app.features.policy.dto.request.PolicyRevokeRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var policy = policies.findLockedById(policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (policy.getDeletedAt() != null) throw notFound();
        var version = versions.findById(versionId).filter(v -> v.getPolicyId() == policyId).orElseThrow(PolicyServiceImpl::notFound);
        if (version.getRevocation() == null) {
            var revocation = new com.trekking.app.features.policy.entity.PolicyVersionRevocation();
            revocation.setPolicyVersionId(versionId);
            revocation.setRevokedBy(authorization.currentAccountId());
            revocation.setRevokedAt(OffsetDateTime.now(clock));
            revocation.setReason(request.reason().trim());
            revocations.saveAndFlush(revocation);
            version.setRevocation(revocation);
            audit(policyId, "POLICY_VERSION_REVOKED", Map.of("versionId", versionId, "reason", request.reason().trim()));
        }
        return response(policy, version);
    }

    @Override
    public PolicyResponse create(PolicyCreateRequest request) {
        authorization.require(Permission.POLICY_MANAGE);
        var kind = POLICY_KINDS.stream().filter(candidate -> candidate.getValue().equals(request.kind())).findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_POLICY_KIND", "Unsupported customer policy kind"));
        if (policies.findAllByOrderByIdAsc().stream().anyMatch(p -> p.getCode().equalsIgnoreCase(request.code().trim()))) {
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
        if (policy.getDeletedAt() != null) throw notFound();
        if (!Boolean.TRUE.equals(policy.getActive())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_NOT_PUBLISHABLE", "Only active customer policies can be published");
        }
        // PostgreSQL stores microseconds. Compare the same precision that will be persisted.
        var effectiveFrom = request.effectiveFrom().truncatedTo(ChronoUnit.MICROS);
        var previous = versions.findAllByPolicyIdOrderByVersionNumberDesc(policyId).stream()
                .filter(v -> v.getRevocation() == null).findFirst();
        if (previous.isPresent() && !effectiveFrom.isAfter(previous.get().getEffectiveFrom())) {
            throw new AuthException(HttpStatus.CONFLICT, "POLICY_EFFECTIVE_FROM_OUT_OF_ORDER",
                    "A new policy version must become effective after the previous version");
        }
        var version = new PolicyVersion();
        version.setPolicyId(policyId);
        // Next version = latest version number + 1 (or 1 for the first version).
        int nextVersionNumber = 1;
        Optional<PolicyVersion> latestVersion = versions.findFirstByPolicyIdOrderByVersionNumberDesc(policyId);
        if (latestVersion.isPresent()) {
            nextVersionNumber = latestVersion.get().getVersionNumber() + 1;
        }
        version.setVersionNumber(nextVersionNumber);
        version.setContent(request.content());
        version.setRefundTiers(refundTiers(policy, request));
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
        Map<Long, PolicyVersionSummary> latestVersions = versions.findLatestSummaries().stream()
                .collect(Collectors.toMap(PolicyVersionSummary::policyId, Function.identity()));
        return policies.findAllByOrderByIdAsc().stream().filter(p -> p.getDeletedAt() == null)
                .map(policy -> response(policy, latestVersions.get(policy.getId()))).toList();
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
        return policies.findById(id).filter(p -> p.getDeletedAt() == null).orElseThrow(PolicyServiceImpl::notFound);
    }

    private static PolicyResponse response(Policy policy) {
        return response(policy, (PolicyVersionSummary) null);
    }

    private static PolicyResponse response(Policy policy, PolicyVersionSummary latestVersion) {
        var updatedAt = policy.getUpdatedAt();
        if (latestVersion != null && latestVersion.approvedAt() != null
                && (updatedAt == null || latestVersion.approvedAt().isAfter(updatedAt))) {
            updatedAt = latestVersion.approvedAt();
        }
        if (latestVersion != null && latestVersion.revokedAt() != null
                && (updatedAt == null || latestVersion.revokedAt().isAfter(updatedAt))) {
            updatedAt = latestVersion.revokedAt();
        }
        return new PolicyResponse(policy.getId(), policy.getCode(), policy.getTitle(), policy.getKind().getValue(),
                policy.getActive(), latestVersion == null ? null : latestVersion.versionNumber(), updatedAt);
    }

    private PolicyVersionResponse response(Policy policy, PolicyVersion version) {
        return new PolicyVersionResponse(version.getId(), policy.getId(), policy.getCode(), policy.getTitle(),
                version.getVersionNumber(), version.getContent(), version.getEffectiveFrom(), version.getApprovedBy(), version.getApprovedAt(),
                version.getRevocation() == null ? null : version.getRevocation().getRevokedAt(),
                version.getRevocation() == null ? null : version.getRevocation().getReason(), policy.getKind().getValue(),
                version.getRefundTiers() == null ? null
                        : List.of(json.readValue(version.getRefundTiers(), RefundTierRequest[].class)));
    }

    /** Validates and serializes tiers sorted by the strictest (largest) notice period first. */
    private String refundTiers(Policy policy, PolicyPublishRequest request) {
        var tiers = request.refundTiers();
        if (tiers == null || tiers.isEmpty()) return null;
        if (policy.getKind() != PolicyKind.CANCELLATION) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "REFUND_TIERS_NOT_ALLOWED",
                    "Refund tiers are only allowed on cancellation policies");
        }
        var sorted = tiers.stream()
                .sorted(Comparator.comparing(RefundTierRequest::minHoursBeforeDeparture).reversed()).toList();
        for (int i = 1; i < sorted.size(); i++) {
            var stricter = sorted.get(i - 1);
            var looser = sorted.get(i);
            if (stricter.minHoursBeforeDeparture().equals(looser.minHoursBeforeDeparture())) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "REFUND_TIERS_INVALID",
                        "Refund tiers must use distinct hour thresholds");
            }
            if (stricter.refundPercent() < looser.refundPercent()) {
                throw new AuthException(HttpStatus.BAD_REQUEST, "REFUND_TIERS_INVALID",
                        "Cancelling earlier must never refund less than cancelling later");
            }
        }
        return json.writeValueAsString(sorted);
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
