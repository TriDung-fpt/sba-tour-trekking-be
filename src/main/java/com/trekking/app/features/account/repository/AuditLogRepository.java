package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Audit persistence exposes insertion to writers and read-only queries for the admin log API. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog saveAndFlush(AuditLog audit);

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
              and (:actorId is null or a.actorId = :actorId)
            order by a.occurredAt desc, a.id desc
            """)
    Page<AuditLog> search(@Param("entityType") String entityType, @Param("entityId") Long entityId,
                          @Param("action") String action, @Param("actorId") Long actorId,
                          Pageable pageable);
}
