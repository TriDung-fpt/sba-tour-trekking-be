package com.trekking.app.features.equipment.dto.response;

public final class EquipmentResponses {
    private EquipmentResponses() {}

    public record EquipmentDetails(long id, String name, String description, Long categoryId,
                                   String categoryName, int totalQuantity, int maintenanceQuantity,
                                   int availableQuantity, boolean active) {}

    public record Category(long id, String name, String description, boolean active) {}
}
