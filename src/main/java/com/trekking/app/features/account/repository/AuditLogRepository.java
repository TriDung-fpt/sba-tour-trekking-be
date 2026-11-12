package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AuditLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Audit rows are append-only: callers may insert and read, never update or delete. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog saveAndFlush(AuditLog audit);

    List<AuditLog> findAllByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(String entityType,
                                                                              Collection<Long> entityIds);
}
