package com.trekking.app.features.equipment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record EquipmentHandoverRequest(String note, @NotEmpty List<@Valid Item> items) {
    public record Item(@Positive long equipmentId, @Positive int quantity, Condition condition) {}
    public enum Condition { GOOD, NEEDS_INSPECTION }
}
