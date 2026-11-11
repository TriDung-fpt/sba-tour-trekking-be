package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.PolicyVersion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    List<PolicyVersion> findAllByPolicyIdOrderByVersionNumberDesc(Long policyId);

    Optional<PolicyVersion> findFirstByPolicyIdOrderByVersionNumberDesc(Long policyId);

    Optional<PolicyVersion> findFirstByPolicyIdAndApprovedByIsNotNullAndApprovedAtLessThanEqualAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(
            Long policyId, OffsetDateTime approvedAt, OffsetDateTime effectiveFrom);

    /** Derived query ("Locked" is only a label); the row is read with SELECT ... FOR UPDATE. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PolicyVersion> findLockedById(Long id);
}
