package com.trekking.app.features.tour.repository;

import com.trekking.app.features.company.entity.PolicyVersion;
import com.trekking.app.features.tour.dto.response.CancellationPolicyVersion;
import com.trekking.app.features.tour.dto.response.TourPolicySummary;
import com.trekking.app.features.company.enums.PolicyKind;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourPolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {
    @Query("""
            select case when count(v) > 0 then true else false end
            from PolicyVersion v join v.policy p
            where v.id = :versionId and p.active = true and p.kind = :kind
              and (p.companyId is null or p.companyId = :companyId)
              and v.approvedBy is not null and v.approvedAt is not null and v.effectiveFrom <= :now
            """)
    boolean isValidCancellationPolicy(@Param("versionId") Long versionId,
                                     @Param("companyId") Long companyId,
                                     @Param("kind") PolicyKind kind,
                                     @Param("now") OffsetDateTime now);

    @Query("""
            select new com.trekking.app.features.tour.dto.response.CancellationPolicyVersion(
                v.id, p.title, v.versionNumber, v.content, v.effectiveFrom)
            from PolicyVersion v join v.policy p
            where p.active = true and p.kind = :kind
              and (p.companyId is null or p.companyId = :companyId)
              and v.approvedBy is not null and v.approvedAt is not null and v.effectiveFrom <= :now
            order by p.title, v.versionNumber desc, v.id desc
            """)
    List<CancellationPolicyVersion> findAvailableCancellationPolicies(@Param("companyId") Long companyId,
                                                                       @Param("kind") PolicyKind kind,
                                                                       @Param("now") OffsetDateTime now);

    @Query("""
            select new com.trekking.app.features.tour.dto.response.TourPolicySummary(
                v.id, p.title, v.versionNumber, v.content)
            from PolicyVersion v join v.policy p
            where v.id = :versionId and p.kind = :kind and p.active = true
              and v.approvedAt is not null
            """)
    java.util.Optional<TourPolicySummary> findPublicCancellationPolicy(@Param("versionId") Long versionId,
                                                                        @Param("kind") PolicyKind kind);
}
