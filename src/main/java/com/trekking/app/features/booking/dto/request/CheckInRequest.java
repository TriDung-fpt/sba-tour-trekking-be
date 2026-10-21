package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.Size;

public record CheckInRequest(@Size(max = 500) String note) {}
