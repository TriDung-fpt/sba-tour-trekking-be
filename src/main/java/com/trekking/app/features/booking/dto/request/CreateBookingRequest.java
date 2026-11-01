package com.trekking.app.features.booking.dto.request;

import com.trekking.app.features.booking.enums.BookingEquipmentChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateBookingRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull @Positive Long scheduleId,
        @NotNull BookingEquipmentChoice equipmentChoice,
        @Size(max = 100) Map<@Positive Long, @Positive Integer> packageQuantities,
        @Size(max = 100) Map<@Positive Long, @Positive Integer> itemQuantities,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid BookingPassengerRequest> passengers
) {}
