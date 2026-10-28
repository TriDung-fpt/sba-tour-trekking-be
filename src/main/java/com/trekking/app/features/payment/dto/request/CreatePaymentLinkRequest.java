package com.trekking.app.features.payment.dto.request;

import com.trekking.app.features.payment.enums.PaymentPurpose;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentLinkRequest(@NotNull PaymentPurpose purpose) {}
