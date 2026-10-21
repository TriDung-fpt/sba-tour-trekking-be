package com.trekking.app.features.payment.dto.request;

import com.trekking.app.features.payment.enums.PaymentPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record ManualPaymentRequest(@NotNull PaymentPurpose purpose, @NotNull @Positive Long amountVnd,
        @NotBlank @Size(max = 200) String transactionReference, @NotNull OffsetDateTime receivedAt,
        @NotBlank @Size(max = 2000) String evidenceUrl, @NotBlank @Size(max = 1000) String reason) {}
