package com.trekking.app.features.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBookingRequest(
        @NotNull @Positive Long scheduleId,
        @NotEmpty @Size(max = 1_000_000) List<@NotNull @Valid TravelerRequest> travelers,
        @NotNull @Valid RentalSelectionRequest rental) {}
