package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AgreementOtpRequest(@NotNull @Positive Long policyVersionId) {}
