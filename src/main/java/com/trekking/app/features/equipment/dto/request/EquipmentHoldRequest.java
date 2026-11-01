package com.trekking.app.features.equipment.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;

public record EquipmentHoldRequest(@Positive long bookingId, @Positive long scheduleId,
                                   @NotNull OffsetDateTime holdExpiresAt,
                                   @Positive int participantCount,
                                   Map<Long, @Positive Integer> packageQuantities,
                                   Map<Long, @Positive Integer> itemQuantities) {}
