package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyStatement;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyStatementRepository extends JpaRepository<CompanyStatement, Long> {
    Page<CompanyStatement> findAllByCompanyIdIn(Collection<Long> companyIds, Pageable pageable);
}
