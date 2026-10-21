package com.trekking.app.features.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FinancialPermissionsRequest(
        @NotNull @Size(max = 7) List<@NotBlank String> permissions,
        @NotBlank @Size(max = 2000) String reason) {
    public FinancialPermissionsRequest { reason = reason == null ? null : reason.strip(); }
}
