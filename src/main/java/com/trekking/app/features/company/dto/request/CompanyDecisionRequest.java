package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyDecisionRequest(
        @NotBlank @Pattern(regexp = "approve|reject|suspend") String decision,
        @NotBlank @Size(max = 2000) String reason) {}
