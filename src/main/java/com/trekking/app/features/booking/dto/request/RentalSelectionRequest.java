package com.trekking.app.features.booking.dto.request;

import com.trekking.app.features.booking.enums.RentalMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RentalSelectionRequest(
        @NotNull RentalMode mode,
        @Positive Long packageId,
        @Positive Integer quantity,
        List<@NotNull @Valid RentalItemSelectionRequest> items) {}
