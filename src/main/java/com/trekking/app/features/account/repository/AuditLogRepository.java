package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AuditLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @Query(value = "select * from trekking.audit_logs where action = :action and details ->> 'idempotencyKey' = :key order by id desc limit 1", nativeQuery = true)
    Optional<AuditLog> findIdempotent(@Param("action") String action, @Param("key") String key);
}
