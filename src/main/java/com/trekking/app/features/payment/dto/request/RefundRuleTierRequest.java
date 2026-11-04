package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RefundRuleTierRequest(@Min(0) int minimumHoursBeforeDeparture,
                                    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal refundPercent) {}
