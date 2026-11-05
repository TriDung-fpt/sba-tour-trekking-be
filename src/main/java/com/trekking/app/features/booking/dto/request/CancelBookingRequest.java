package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(@NotBlank @Size(max = 1000) String reason) {}
