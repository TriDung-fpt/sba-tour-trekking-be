package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RefundSubmissionRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6,20}") String bankBin,
        @NotBlank @Pattern(regexp = "[0-9]{5,30}") String accountNumber,
        @NotBlank @Size(min = 2, max = 120) String accountHolderName) {}
