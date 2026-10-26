package com.trekking.app.features.booking.dto.response;

/** {@code created} is false when an idempotent retry returned the existing booking. */
public record BookingCreateResult(boolean created, BookingDetailResponse booking) {}
