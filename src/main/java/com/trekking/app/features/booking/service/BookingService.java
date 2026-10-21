package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.response.CreatedBookingResponse;

public interface BookingService {
    CreatedBookingResponse create(long accountId, String idempotencyKey, CreateBookingRequest request);
}
