package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyStatement;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyStatementRepository extends JpaRepository<CompanyStatement, Long> {
    Page<CompanyStatement> findAllByCompanyIdIn(Collection<Long> companyIds, Pageable pageable);
    Optional<CompanyStatement> findByCompanyIdAndPeriodStartAndPeriodEnd(Long companyId,
            java.time.LocalDate periodStart, java.time.LocalDate periodEnd);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CompanyStatement s where s.id = :id")
    Optional<CompanyStatement> lockById(@Param("id") long id);
    @Query("select s from CompanyStatement s where (:status is null or s.status = :status) and (:companyId is null or s.companyId = :companyId)")
    Page<CompanyStatement> search(@Param("status") com.trekking.app.features.statement.enums.StatementStatus status,
            @Param("companyId") Long companyId, Pageable pageable);
}
