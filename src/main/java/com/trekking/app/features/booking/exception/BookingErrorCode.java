package com.trekking.app.features.booking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BookingErrorCode {
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "Schedule not found"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was already used with a different request"),
    BOOKING_NOT_AVAILABLE(HttpStatus.CONFLICT, "Schedule is not open for booking"),
    INSUFFICIENT_SEATS(HttpStatus.CONFLICT, "Not enough seats left on this schedule"),
    BOOKING_HOLD_EXPIRED(HttpStatus.CONFLICT, "Booking hold has expired"),
    BOOKING_NOT_EDITABLE(HttpStatus.CONFLICT, "Booking can no longer be changed"),
    INVALID_BOOKING_TRANSITION(HttpStatus.CONFLICT, "Booking cannot move to the requested status"),
    EQUIPMENT_PACKAGE_NOT_AVAILABLE(HttpStatus.CONFLICT, "Equipment package is not available for this schedule"),
    EQUIPMENT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Equipment service is temporarily unavailable"),
    PASSENGER_COUNT_MISMATCH(HttpStatus.BAD_REQUEST, "Passenger list does not match the booked seat count"),
    MULTIPLE_BOOKING_CUSTOMERS(HttpStatus.BAD_REQUEST, "At most one passenger can be the booking customer");

    private final HttpStatus status;
    private final String defaultMessage;

    BookingErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
