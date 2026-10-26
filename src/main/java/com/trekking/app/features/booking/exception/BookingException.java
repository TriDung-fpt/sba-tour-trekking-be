package com.trekking.app.features.booking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BookingException extends RuntimeException {
    private final BookingErrorCode errorCode;

    public BookingException(BookingErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BookingException(BookingErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }

    public String getCode() {
        return errorCode.name();
    }

    public static BookingException notFound() {
        return new BookingException(BookingErrorCode.BOOKING_NOT_FOUND);
    }

    public static BookingException insufficientSeats(int remaining) {
        return new BookingException(BookingErrorCode.INSUFFICIENT_SEATS,
                "Only " + remaining + " seat(s) left on this schedule");
    }
}
