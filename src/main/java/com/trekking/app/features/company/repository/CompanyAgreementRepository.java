package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.CompanyAgreement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyAgreementRepository extends JpaRepository<CompanyAgreement, Long> {
    Optional<CompanyAgreement> findFirstByCompanyIdOrderByAcceptedAtDesc(Long companyId);
}
