package com.trekking.app.features.statement.dto.request;

import jakarta.validation.constraints.Size;

public record ApproveStatementRequest(@Size(max = 1000) String note) {}
