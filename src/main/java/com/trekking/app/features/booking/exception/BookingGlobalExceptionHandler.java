package com.trekking.app.features.booking.exception;

import com.trekking.app.core.exception.ApiError;
import com.trekking.app.features.booking.controller.BookingController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = BookingController.class)
public class BookingGlobalExceptionHandler {
    @ExceptionHandler(BookingException.class)
    ResponseEntity<ApiError> booking(BookingException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        log.warn("Booking database constraint failed", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("BOOKING_DATA_CONFLICT", "Booking data conflicts with current schedule state"));
    }
}
