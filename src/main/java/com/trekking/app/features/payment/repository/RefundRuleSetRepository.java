package com.trekking.app.features.payment.repository;

import com.trekking.app.features.payment.entity.RefundRuleSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRuleSetRepository extends JpaRepository<RefundRuleSet, Long> {
    Optional<RefundRuleSet> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(OffsetDateTime at);
}
