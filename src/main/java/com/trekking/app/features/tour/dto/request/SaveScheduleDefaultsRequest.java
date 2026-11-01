package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record SaveScheduleDefaultsRequest(@NotNull @Positive Integer capacity,
        @NotNull @PositiveOrZero @Max(1000000000000L) Long basePriceVnd,
        @NotNull @PositiveOrZero @Max(1000000000000L) Long equipmentFeeVnd,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal depositPercent,
        String meetingPoint, @Size(max = 30) String contactPhone) {}
