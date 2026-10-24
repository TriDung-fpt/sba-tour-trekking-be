package com.trekking.app.features.tour.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TourException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public TourException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public TourException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }
}
