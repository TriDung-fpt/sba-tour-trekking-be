package com.trekking.app.features.booking.exception;

import com.trekking.app.core.exception.ApiError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.trekking.app.features.booking")
public class BookingExceptionHandler {
    @ExceptionHandler(BookingException.class)
    ResponseEntity<ApiError> booking(BookingException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage()));
    }
}
