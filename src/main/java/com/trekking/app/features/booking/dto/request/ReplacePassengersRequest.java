package com.trekking.app.features.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplacePassengersRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid BookingPassengerRequest> passengers
) {}
