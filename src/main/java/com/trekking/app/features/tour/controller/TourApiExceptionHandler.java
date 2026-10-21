package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.service.TourApiException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.trekking.app.features.tour.controller")
public class TourApiExceptionHandler {
    @ExceptionHandler(TourApiException.class)
    ResponseEntity<Map<String, Object>> handle(TourApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of(
                "code", ex.code(), "message", ex.getMessage(), "fieldErrors", Map.of()));
    }
}
