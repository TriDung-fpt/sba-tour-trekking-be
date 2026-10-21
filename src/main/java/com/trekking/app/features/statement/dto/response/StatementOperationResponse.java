package com.trekking.app.features.statement.dto.response;

public record StatementOperationResponse(long statementId, String status, long payableVnd,
        long transferredVnd, long remainingVnd) {}
