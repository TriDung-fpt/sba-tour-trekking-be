package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.Positive;
import java.util.List;

public record AssignTourEquipmentRequest(
        @Positive Long packageTemplateId,
        List<@Positive Long> individualEquipmentIds) {}
