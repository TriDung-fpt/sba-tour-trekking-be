package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CancellationRefundRuleRequest(
        @Min(0) @Max(3650) int daysBeforeDeparture,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal refundPercent) {}
