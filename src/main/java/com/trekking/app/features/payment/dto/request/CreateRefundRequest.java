package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(@NotBlank @Size(max = 1000) String reason) {}
