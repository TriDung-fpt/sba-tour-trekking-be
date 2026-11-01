package com.trekking.app.features.booking.dto.response;

public record BookingPassengerResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        boolean bookingCustomer
) {}
