package com.trekking.app.features.payment.dto.request;

import com.trekking.app.features.payment.enums.RefundChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record RecordRefundRequest(@NotNull @Positive Long amountVnd, @NotNull RefundChannel channel,
        @NotBlank @Size(max = 200) String transactionReference, @NotNull OffsetDateTime refundedAt,
        Long originalPaymentId, @NotBlank @Size(max = 2000) String evidenceUrl) {}
