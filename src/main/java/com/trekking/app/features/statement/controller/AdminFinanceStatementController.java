package com.trekking.app.features.statement.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.statement.dto.request.ApproveStatementRequest;
import com.trekking.app.features.statement.dto.request.CreateStatementRequest;
import com.trekking.app.features.statement.dto.request.RecordTransferRequest;
import com.trekking.app.features.statement.dto.response.AdminCompanyPage;
import com.trekking.app.features.statement.dto.response.AdminStatementPage;
import com.trekking.app.features.statement.dto.response.StatementOperationResponse;
import com.trekking.app.features.statement.service.FinanceStatementOperationsService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFinanceStatementController {
    private final FinanceStatementOperationsService statements;

    public AdminFinanceStatementController(FinanceStatementOperationsService statements) { this.statements = statements; }

    @GetMapping("/finance/companies")
    public AdminCompanyPage eligibleCompanies(@RequestParam LocalDate periodStart, @RequestParam LocalDate periodEnd,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return statements.eligibleCompanies(periodStart, periodEnd, page, size);
    }

    @GetMapping("/statements")
    public AdminStatementPage list(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return statements.list(status, companyId, page, size);
    }

    @PostMapping("/statements")
    public StatementOperationResponse create(@AuthenticationPrincipal UserPrincipal admin,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateStatementRequest request) {
        return statements.create(admin.accountId(), key, request);
    }

    @PostMapping("/statements/{statementId}/approve")
    public StatementOperationResponse approve(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long statementId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody(required = false) ApproveStatementRequest request) {
        return statements.approve(admin.accountId(), statementId, key, request);
    }

    @PostMapping("/statements/{statementId}/transfers")
    public StatementOperationResponse recordTransfer(@AuthenticationPrincipal UserPrincipal admin,
            @PathVariable long statementId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody RecordTransferRequest request) {
        return statements.recordTransfer(admin.accountId(), statementId, key, request);
    }
}
