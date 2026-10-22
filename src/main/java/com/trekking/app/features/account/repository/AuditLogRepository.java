package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AuditLog;
import org.springframework.data.repository.Repository;

/** Audit persistence deliberately exposes only insertion to application callers. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {
    AuditLog saveAndFlush(AuditLog audit);
}
