package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.PolicyVersion;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    Optional<PolicyVersion> findFirstByPolicyIdAndApprovedByIsNotNullAndApprovedAtLessThanEqualAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(
            Long policyId, OffsetDateTime approvedAt, OffsetDateTime effectiveFrom);

    @Query("select coalesce(max(v.versionNumber), 0) from PolicyVersion v where v.policyId = :policyId")
    int lastVersionNumber(@Param("policyId") Long policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PolicyVersion v where v.id = :id")
    Optional<PolicyVersion> findLockedById(@Param("id") Long id);
}
