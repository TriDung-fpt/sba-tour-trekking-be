package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.enums.CompanyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByTaxCode(String taxCode);
    List<Company> findAllByStatusOrderByCreatedAtAsc(CompanyStatus status);
}
