package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record AssignTourEquipmentRequest(
        @PositiveOrZero Long version,
        List<@NotNull @Positive Long> packageTemplateIds,
        @Positive Long packageTemplateId,
        List<@NotNull @Positive Long> individualEquipmentIds) {

    public AssignTourEquipmentRequest(
            List<@NotNull @Positive Long> packageTemplateIds,
            @Positive Long packageTemplateId,
            List<@NotNull @Positive Long> individualEquipmentIds) {
        this(null, packageTemplateIds, packageTemplateId, individualEquipmentIds);
    }

    public AssignTourEquipmentRequest(
            @PositiveOrZero Long version,
            List<@NotNull @Positive Long> packageTemplateIds,
            List<@NotNull @Positive Long> individualEquipmentIds) {
        this(version, packageTemplateIds, null, individualEquipmentIds);
    }

    public AssignTourEquipmentRequest(
            List<@NotNull @Positive Long> packageTemplateIds,
            List<@NotNull @Positive Long> individualEquipmentIds) {
        this(null, packageTemplateIds, null, individualEquipmentIds);
    }

    public AssignTourEquipmentRequest(
            @Positive Long packageTemplateId,
            List<@NotNull @Positive Long> individualEquipmentIds) {
        this(null, null, packageTemplateId, individualEquipmentIds);
    }
}
