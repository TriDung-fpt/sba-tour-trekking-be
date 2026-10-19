package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.Company;
import com.trekking.app.features.company.enums.CompanyStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Company c where c.id = :id")
    Optional<Company> findLockedById(@Param("id") Long id);

    boolean existsByTaxCodeAndIdNot(String taxCode, Long id);

    Page<Company> findByStatus(CompanyStatus status, Pageable pageable);

    @Query("select c from Company c where exists (select m from CompanyMember m where m.companyId = c.id "
            + "and m.accountId = :accountId and m.active = true and m.memberRole in "
            + "(com.trekking.app.features.company.enums.CompanyMemberRole.OWNER, "
            + "com.trekking.app.features.company.enums.CompanyMemberRole.MANAGER))")
    Page<Company> findManagedBy(@Param("accountId") Long accountId, Pageable pageable);
}
