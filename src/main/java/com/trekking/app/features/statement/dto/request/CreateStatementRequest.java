package com.trekking.app.features.statement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateStatementRequest(@NotNull Long companyId, @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd, @Size(max = 1000) String note) {}
