package com.trekking.app.features.invitation.repository;

import com.trekking.app.features.account.entity.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationAuditRepository extends JpaRepository<AuditLog, Long> {
    boolean existsByEntityTypeAndEntityIdAndAction(String entityType, Long entityId, String action);
    Optional<AuditLog> findFirstByEntityTypeAndEntityIdAndActionOrderByIdDesc(String entityType, Long entityId, String action);
}
