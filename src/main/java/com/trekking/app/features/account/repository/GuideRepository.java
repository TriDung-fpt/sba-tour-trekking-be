package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.Guide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    Optional<Guide> findByAccountIdAndActiveTrue(Long accountId);
}
