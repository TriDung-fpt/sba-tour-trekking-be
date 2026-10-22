package com.trekking.app.features.company.repository;

import com.trekking.app.features.company.entity.Policy;
import com.trekking.app.features.company.enums.PolicyKind;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Policy p where p.id = :id")
    Optional<Policy> findLockedById(@Param("id") Long id);

    @Query("select p from Policy p where p.active = true and p.kind = :kind and (p.companyId is null or p.companyId = :companyId) order by p.id")
    List<Policy> findApplicable(@Param("kind") PolicyKind kind, @Param("companyId") Long companyId);

    boolean existsByCode(String code);
}
