package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.entity.CompanyMemberId;
import java.util.Optional;
import java.util.List;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, CompanyMemberId> {
    Optional<CompanyMember> findFirstByAccountIdAndActiveTrueOrderByCreatedAtDesc(Long accountId);
    Optional<CompanyMember> findFirstByCompanyIdAndActiveTrue(Long companyId);
    List<CompanyMember> findAllByAccountIdAndActiveTrue(Long accountId);
    Optional<CompanyMember> findByCompanyIdAndAccountIdAndActiveTrue(Long companyId, Long accountId);
}
