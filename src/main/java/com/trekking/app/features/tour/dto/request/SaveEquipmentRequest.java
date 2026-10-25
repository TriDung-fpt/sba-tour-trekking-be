package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveEquipmentRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @PositiveOrZero Long individualRentalPriceVnd) {}
