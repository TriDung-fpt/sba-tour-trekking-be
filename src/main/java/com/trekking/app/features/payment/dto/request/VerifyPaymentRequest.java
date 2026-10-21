package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPaymentRequest(@NotBlank @Size(max = 2000) String evidenceUrl,
        @NotBlank @Size(max = 1000) String reason) {}
