package com.trekking.app.features.equipment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EquipmentRequests {
    private EquipmentRequests() {}

    public record SaveInventory(@NotNull Long categoryId,
                                @NotNull @PositiveOrZero Integer totalQuantity,
                                @NotNull @PositiveOrZero Integer maintenanceQuantity,
                                @NotNull Boolean active) {}

    public record SaveCategory(@NotBlank @Size(max = 120) String name, String description, Boolean active) {}

}
