package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.CompanyAgreement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyAgreementRepository extends JpaRepository<CompanyAgreement, Long> {
    boolean existsByCompanyIdAndPolicyVersionId(Long companyId, Long policyVersionId);
    List<CompanyAgreement> findByCompanyIdOrderByAcceptedAtDescIdDesc(Long companyId);
    Optional<CompanyAgreement> findByIdAndCompanyId(Long id, Long companyId);
}
