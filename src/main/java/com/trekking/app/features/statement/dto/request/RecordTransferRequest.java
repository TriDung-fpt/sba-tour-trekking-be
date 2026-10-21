package com.trekking.app.features.statement.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record RecordTransferRequest(@NotNull @Positive Long amountVnd,
        @NotBlank @Size(max = 200) String bankReference, @NotNull OffsetDateTime transferredAt,
        @NotBlank @Size(max = 2000) String evidenceUrl) {}
