package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.Policy;
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

    List<Policy> findAllByOrderByIdAsc();

    boolean existsByCode(String code);
}
