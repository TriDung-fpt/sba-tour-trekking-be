package com.trekking.app.features.policy.repository;

import com.trekking.app.features.policy.entity.Policy;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    /** Derived query ("Locked" is only a label); the row is read with SELECT ... FOR UPDATE. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Policy> findLockedById(Long id);

    List<Policy> findAllByOrderByIdAsc();

    boolean existsByCode(String code);
}
