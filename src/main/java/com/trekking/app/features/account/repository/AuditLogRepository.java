package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
