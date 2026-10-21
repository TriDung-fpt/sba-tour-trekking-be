package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyTransfer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyTransferRepository extends JpaRepository<CompanyTransfer, Long> {
    List<CompanyTransfer> findAllByStatementIdOrderByTransferredAtAscIdAsc(Long statementId);
}
