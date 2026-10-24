package com.trekking.app.features.tour.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TourException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public TourException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
