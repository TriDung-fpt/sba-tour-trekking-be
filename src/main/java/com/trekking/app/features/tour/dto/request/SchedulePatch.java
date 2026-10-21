package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SchedulePatch(OffsetDateTime startsAt, OffsetDateTime endsAt, @Positive Integer capacity,
                                @PositiveOrZero @Max(1000000000000L) Long basePriceVnd, @PositiveOrZero @Max(1000000000000L) Long equipmentFeeVnd,
                                @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal depositPercent,
                                OffsetDateTime balanceDueAt, @Size(max = 2000) String meetingPoint,
                                @Size(max = 30) String contactPhone, @Valid @Size(max = 20) List<GuideAssignment> guideAssignments) {}
