package com.trekking.app.features.payment.dto.request;

import com.trekking.app.features.payment.enums.PaymentPurpose;
import com.trekking.app.features.payment.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentLinkRequest(@NotNull PaymentProvider provider, @NotNull PaymentPurpose purpose) {}
