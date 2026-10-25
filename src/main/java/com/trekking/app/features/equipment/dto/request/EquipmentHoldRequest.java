package com.trekking.app.features.equipment.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;

public record EquipmentHoldRequest(@Positive long bookingId, @Positive long scheduleId,
                                   @NotNull Mode mode, Long packageId, Integer packageQuantity,
                                   @Positive int participantCount, @NotNull OffsetDateTime holdExpiresAt,
                                   Map<Long, @Positive Integer> itemQuantities) {
    public enum Mode { NONE, PACKAGE, ITEMS }
}
