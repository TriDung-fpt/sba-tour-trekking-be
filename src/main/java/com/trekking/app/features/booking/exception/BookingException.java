package com.trekking.app.features.booking.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BookingException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BookingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
