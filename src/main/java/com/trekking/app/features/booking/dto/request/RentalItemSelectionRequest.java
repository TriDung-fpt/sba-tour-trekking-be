package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RentalItemSelectionRequest(@NotNull @Positive Long itemId, @NotNull @Positive Integer quantity) {}
