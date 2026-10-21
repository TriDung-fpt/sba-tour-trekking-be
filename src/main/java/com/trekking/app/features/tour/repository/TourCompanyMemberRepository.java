package com.trekking.app.features.tour.repository;

import com.trekking.app.features.company.entity.CompanyMember;
import com.trekking.app.features.company.entity.CompanyMemberId;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.enums.CompanyStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourCompanyMemberRepository extends JpaRepository<CompanyMember, CompanyMemberId> {
    List<CompanyMember> findAllByAccountIdAndActiveTrueAndMemberRoleInAndCompany_StatusOrderByCompanyIdAsc(
            Long accountId, Collection<CompanyMemberRole> memberRoles, CompanyStatus status);

    boolean existsByCompanyIdAndAccountIdAndActiveTrueAndCompany_Status(Long companyId, Long accountId, CompanyStatus status);

    boolean existsByCompanyIdAndAccountIdAndActiveTrueAndMemberRoleInAndCompany_Status(
            Long companyId, Long accountId, Collection<CompanyMemberRole> memberRoles, CompanyStatus status);
}
