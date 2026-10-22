package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.entity.CompanyMemberId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, CompanyMemberId> {
    Optional<CompanyMember> findByCompanyIdAndAccountIdAndActiveTrue(Long companyId, Long accountId);

    @Query("select m from CompanyMember m join fetch m.company where m.accountId = :accountId and m.active = true order by m.companyId")
    List<CompanyMember> findActiveContexts(@Param("accountId") Long accountId);
}
