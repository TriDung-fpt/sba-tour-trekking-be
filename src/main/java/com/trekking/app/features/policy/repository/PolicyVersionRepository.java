package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.PolicyVersion;
import com.trekking.app.features.policy.dto.response.PolicyVersionSummary;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    List<PolicyVersion> findAllByPolicyIdOrderByVersionNumberDesc(Long policyId);

    @Query("select new com.trekking.app.features.policy.dto.response.PolicyVersionSummary(v.policyId, v.versionNumber, v.approvedAt, revocation.revokedAt) " +
            "from PolicyVersion v left join v.revocation revocation " +
            "where v.versionNumber = (select max(latest.versionNumber) from PolicyVersion latest where latest.policyId = v.policyId)")
    List<PolicyVersionSummary> findLatestSummaries();

    Optional<PolicyVersion> findFirstByPolicyIdOrderByVersionNumberDesc(Long policyId);
    Optional<PolicyVersion> findFirstByPolicyIdAndApprovedByIsNotNullAndApprovedAtLessThanEqualAndEffectiveFromLessThanEqualOrderByEffectiveFromDescVersionNumberDesc(
            Long policyId, OffsetDateTime approvedAt, OffsetDateTime effectiveFrom);

    @Query("select coalesce(max(v.versionNumber), 0) from PolicyVersion v where v.policyId = :policyId")
    int lastVersionNumber(@Param("policyId") Long policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from PolicyVersion v where v.id = :id")
    Optional<PolicyVersion> findLockedById(@Param("id") Long id);
}
