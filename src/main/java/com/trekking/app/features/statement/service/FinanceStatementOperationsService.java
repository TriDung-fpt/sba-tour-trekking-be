package com.trekking.app.features.statement.service;

import com.trekking.app.features.statement.dto.request.ApproveStatementRequest;
import com.trekking.app.features.statement.dto.request.CreateStatementRequest;
import com.trekking.app.features.statement.dto.request.RecordTransferRequest;
import com.trekking.app.features.statement.dto.response.AdminCompanyPage;
import com.trekking.app.features.statement.dto.response.AdminStatementPage;
import com.trekking.app.features.statement.dto.response.StatementOperationResponse;
import java.time.LocalDate;

public interface FinanceStatementOperationsService {
    AdminCompanyPage eligibleCompanies(LocalDate periodStart, LocalDate periodEnd, int page, int size);
    AdminStatementPage list(String status, Long companyId, int page, int size);
    StatementOperationResponse create(long adminId, String key, CreateStatementRequest request);
    StatementOperationResponse approve(long adminId, long statementId, String key, ApproveStatementRequest request);
    StatementOperationResponse recordTransfer(long adminId, long statementId, String key, RecordTransferRequest request);
}
