package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Finance confirms a bank transfer made outside the system. */
public record RefundManualTransferRequest(
        @NotBlank @Size(max = 200) String transactionReference,
        @Size(max = 2000) @Pattern(regexp = "https?://\\S+", message = "must be an http(s) URL") String evidenceUrl,
        @Size(max = 1000) String note) {}
