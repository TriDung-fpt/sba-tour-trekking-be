package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import org.springframework.http.HttpStatus;

public class TourApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public TourApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
