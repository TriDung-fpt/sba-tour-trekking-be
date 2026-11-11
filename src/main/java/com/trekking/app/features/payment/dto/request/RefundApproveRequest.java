package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code approvedAmountVnd} defaults to the policy-calculated amount and may only be lowered. */
public record RefundApproveRequest(@Positive Long approvedAmountVnd, @Size(max = 1000) String note) {}
