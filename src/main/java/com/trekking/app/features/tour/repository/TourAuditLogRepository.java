package com.trekking.app.features.tour.repository;

import com.trekking.app.features.account.entity.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourAuditLogRepository extends JpaRepository<AuditLog, Long> {
    Optional<AuditLog> findFirstByEntityTypeAndEntityIdAndActionOrderByOccurredAtDescIdDesc(
            String entityType, Long entityId, String action);
}
