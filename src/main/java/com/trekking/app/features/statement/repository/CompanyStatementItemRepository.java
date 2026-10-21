package com.trekking.app.features.statement.repository;

import com.trekking.app.features.statement.entity.CompanyStatementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyStatementItemRepository extends JpaRepository<CompanyStatementItem, Long> {
    @Query("select coalesce(sum(i.payableVnd), 0) from CompanyStatementItem i where i.statementId = :id")
    long payableTotal(@Param("id") long statementId);
    List<CompanyStatementItem> findAllByStatementIdOrderByIdAsc(Long statementId);
    boolean existsByStatementId(Long statementId);
    List<CompanyStatementItem> findAllByBookingIdAndKindOrderByIdAsc(Long bookingId, com.trekking.app.features.statement.enums.ItemKind kind);
    boolean existsByBookingIdAndKind(Long bookingId, com.trekking.app.features.statement.enums.ItemKind kind);
}
