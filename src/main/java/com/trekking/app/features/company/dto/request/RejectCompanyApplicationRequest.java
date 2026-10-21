package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectCompanyApplicationRequest(@NotBlank @Size(max = 1000) String reason) {
    public RejectCompanyApplicationRequest { reason = reason == null ? null : reason.trim(); }
}
