package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateScheduleRequest(
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt,
        @NotNull @Positive Integer capacity,
        @NotNull @PositiveOrZero @Max(1_000_000_000_000L) Long basePriceVnd,
        @NotNull @PositiveOrZero @Max(1_000_000_000_000L) Long equipmentFeeVnd,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
        @Digits(integer = 3, fraction = 2) BigDecimal depositPercent,
        @NotNull OffsetDateTime balanceDueAt,
        String meetingPoint,
        @Size(max = 30) String contactPhone) {
}
