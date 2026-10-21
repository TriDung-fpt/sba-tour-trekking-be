package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundDecisionRequest(@NotBlank String decision, @Positive Long approvedAmountVnd,
        @NotBlank @Size(max = 1000) String note) {}
