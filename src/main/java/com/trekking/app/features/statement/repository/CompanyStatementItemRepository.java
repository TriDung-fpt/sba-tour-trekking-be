package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyStatementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyStatementItemRepository extends JpaRepository<CompanyStatementItem, Long> {
    @Query("select coalesce(sum(i.payableVnd), 0) from CompanyStatementItem i where i.statementId = :id")
    long payableTotal(@Param("id") long statementId);
}
