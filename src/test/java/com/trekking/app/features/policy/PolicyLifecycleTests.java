package com.trekking.app.features.policy;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.account.repository.AuditLogRepository;
import com.trekking.app.features.policy.dto.request.PolicyRevokeRequest;
import com.trekking.app.features.policy.dto.request.PolicyStatusRequest;
import com.trekking.app.features.policy.entity.Policy;
import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.entity.PolicyVersionRevocation;
import com.trekking.app.features.policy.enums.PolicyKind;
import com.trekking.app.features.policy.repository.*;
import com.trekking.app.features.policy.service.impl.PolicyServiceImpl;
import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyLifecycleTests {
    private final PolicyRepository policies = mock(PolicyRepository.class);
    private final PolicyVersionRepository versions = mock(PolicyVersionRepository.class);
    private final PolicyVersionRevocationRepository revocations = mock(PolicyVersionRevocationRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-30T12:00:00Z");
    private PolicyServiceImpl service;
    private Policy policy;

    @BeforeEach
    void setup() {
        service = new PolicyServiceImpl(authorization, policies, versions, mock(AuditLogRepository.class),
                Clock.fixed(now.toInstant(), ZoneOffset.UTC), JsonMapper.builder().build(), revocations);
        policy = new Policy();
        ReflectionTestUtils.setField(policy, "id", 1L);
        policy.setKind(PolicyKind.CANCELLATION);
        policy.setCode("CANCEL");
        policy.setTitle("Cancellation");
        when(policies.findAllByOrderByIdAsc()).thenReturn(List.of(policy));
        when(policies.findLockedById(1L)).thenReturn(Optional.of(policy));
        when(authorization.currentAccountId()).thenReturn(2L);
    }

    private PolicyVersion version(long id, OffsetDateTime effective) {
        var version = new PolicyVersion();
        ReflectionTestUtils.setField(version, "id", id);
        ReflectionTestUtils.setField(version, "policy", policy);
        version.setPolicyId(1L);
        version.setVersionNumber((int) id);
        version.setContent("Terms");
        version.setApprovedAt(now.minusDays(2));
        version.setEffectiveFrom(effective);
        when(versions.findById(id)).thenReturn(Optional.of(version));
        return version;
    }

    @Test
    void disablingPolicyImmediatelyRejectsAssignmentWithoutChangingVersions() {
        var v = version(1L, now.minusDays(1));
        service.changeStatus(1L, new PolicyStatusRequest(false, "Replace terms"));
        assertThatThrownBy(() -> service.requireEffectiveCancellationVersion(1L)).isInstanceOf(AuthException.class);
        assertThat(v.getContent()).isEqualTo("Terms");
        verify(versions, never()).save(any());
    }

    @Test
    void revokedScheduledVersionDoesNotDisplaceCurrentVersion() {
        var old = version(1L, now.minusDays(1));
        var next = version(2L, now.plusDays(1));
        service.revoke(1L, 2L, new PolicyRevokeRequest("Scheduled terms incorrect"));
        when(versions.findAllByPolicyIdOrderByVersionNumberDesc(1L)).thenReturn(List.of(next, old));
        assertThat(service.effectiveCancellationVersions()).extracting(v -> v.id()).containsExactly(1L);
        assertThatThrownBy(() -> service.requireEffectiveCancellationVersion(2L)).isInstanceOf(AuthException.class);
        verify(revocations).saveAndFlush(any(PolicyVersionRevocation.class));
    }

    @Test
    void revokingCurrentVersionDoesNotSilentlyRestorePreviousTerms() {
        var old = version(1L, now.minusDays(3));
        var current = version(2L, now.minusDays(1));
        service.revoke(1L, 2L, new PolicyRevokeRequest("Stop now"));
        when(versions.findAllByPolicyIdOrderByVersionNumberDesc(1L)).thenReturn(List.of(current, old));
        assertThat(service.effectiveCancellationVersions()).isEmpty();
    }
}
