package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One cancellation refund tier: cancel at least {@code minHoursBeforeDeparture} hours early to get {@code refundPercent}. */
public record RefundTierRequest(@NotNull @Min(0) @Max(87600) Integer minHoursBeforeDeparture,
                                @NotNull @Min(0) @Max(100) Integer refundPercent) {}
