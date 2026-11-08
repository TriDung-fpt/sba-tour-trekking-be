package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.trekking.app.features.tour.enums.EquipmentStatus;

public record SaveEquipmentRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @PositiveOrZero Long individualRentalPriceVnd,
        EquipmentStatus status) {}
