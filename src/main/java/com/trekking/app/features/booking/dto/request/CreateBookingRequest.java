package com.trekking.app.features.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateBookingRequest(@NotNull Long scheduleId, @NotNull Integer participantCount,
                                   @NotNull Boolean includesEquipment,
                                   @NotEmpty @Size(max = 20) List<@Valid BookingParticipantRequest> participants) {}
