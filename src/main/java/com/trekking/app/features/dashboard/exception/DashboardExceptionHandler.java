package com.trekking.app.features.dashboard.exception;

import com.trekking.app.core.exception.ApiError;
import com.trekking.app.features.dashboard.controller.DashboardController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = DashboardController.class)
public class DashboardExceptionHandler {
    @ExceptionHandler(DashboardException.class)
    ResponseEntity<ApiError> dashboard(DashboardException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(ex.getCode(), ex.getMessage()));
    }
}
