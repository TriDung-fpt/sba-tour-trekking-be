package com.trekking.app.features.company.repository;

import com.trekking.app.features.account.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyAuditLogRepository extends JpaRepository<AuditLog, Long> {}
