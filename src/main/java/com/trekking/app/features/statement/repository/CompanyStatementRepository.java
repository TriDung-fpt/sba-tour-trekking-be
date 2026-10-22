package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyStatement;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Locked settlement reads used by the shared finance authorization boundary. */
public interface CompanyStatementRepository extends Repository<CompanyStatement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CompanyStatement s where s.id = :statementId")
    Optional<CompanyStatement> findForUpdate(@Param("statementId") long statementId);
}
