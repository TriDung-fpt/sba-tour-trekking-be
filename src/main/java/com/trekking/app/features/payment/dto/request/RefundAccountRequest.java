package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customer bank destination for a refund. Stored encrypted; never returned to the customer in full. */
public record RefundAccountRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6,8}", message = "must be a 6-8 digit NAPAS bank BIN") String bankBin,
        @NotBlank @Size(max = 120) String bankName,
        @NotBlank @Pattern(regexp = "[0-9]{6,19}", message = "must contain 6-19 digits") String accountNumber,
        @NotBlank @Size(min = 2, max = 120) String accountHolderName) {}
