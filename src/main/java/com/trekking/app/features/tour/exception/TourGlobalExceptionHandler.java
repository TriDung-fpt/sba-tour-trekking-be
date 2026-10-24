package com.trekking.app.features.tour.exception;

import com.trekking.app.core.exception.ApiError;
import com.trekking.app.features.tour.controller.TourController;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.databind.exc.MismatchedInputException;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = TourController.class)
public class TourGlobalExceptionHandler {
    @ExceptionHandler(TourException.class)
    ResponseEntity<ApiError> tour(TourException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiError(ex.getCode(), ex.getMessage(), ex.getFieldErrors()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
                var path = new StringBuilder();
                for (var part : mismatch.getPath()) {
                    if (part.getPropertyName() != null) {
                        if (!path.isEmpty()) path.append('.');
                        path.append(part.getPropertyName());
                    } else if (part.getIndex() >= 0) {
                        path.append('[').append(part.getIndex()).append(']');
                    }
                }
                var type = mismatch.getTargetType();
                var code = type != null && type.isEnum() ? "INVALID_ENUM_VALUE" : "INVALID_FIELD_VALUE";
                return ResponseEntity.badRequest().body(new ApiError(code, "Invalid request field",
                        Map.of(path.toString(), expectedValue(type))));
            }
        }
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Request body must contain valid JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid request parameter",
                Map.of(ex.getName(), expectedValue(ex.getRequiredType()))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Required parameter is missing",
                Map.of(ex.getParameterName(), "This parameter is required")));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Required file is missing",
                Map.of(ex.getRequestPartName(), "This file is required")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        log.warn("Tour database constraint failed", ex);
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                var constraint = violation.getConstraintName();
                var error = switch (constraint == null ? "" : constraint) {
                    case "tours_check" -> new ApiError("TOUR_CANNOT_BE_PUBLISHED",
                            "Tour must be reviewed and have a cancellation policy before publishing",
                            Map.of("status", "Publishing requires reviewedBy, reviewedAt and cancellationPolicyVersionId"));
                    case "tour_activities_tour_id_day_number_display_order_key" -> new ApiError("ACTIVITY_ORDER_CONFLICT",
                            "Another activity already uses this order on the same day",
                            Map.of("displayOrder", "Choose another order for this day"));
                    case "tour_activities_location_id_fkey" -> new ApiError("INVALID_LOCATION",
                            "Activity location does not exist", Map.of("locationId", "Choose an existing location"));
                    case "uq_tour_cover" -> new ApiError("TOUR_COVER_CONFLICT",
                            "Tour already has a cover image", Map.of("mediaId", "Reload the tour and select the cover again"));
                    case "uq_tour_media_cloudinary_public_id" -> new ApiError("TOUR_IMAGE_CONFLICT",
                            "This image is already stored", Map.of("file", "Upload the image again"));
                    case "tours_cancellation_policy_version_id_fkey" -> new ApiError("INVALID_CANCELLATION_POLICY",
                            "Cancellation policy does not exist",
                            Map.of("cancellationPolicyVersionId", "Choose an existing cancellation policy"));
                    case "tours_reviewed_by_fkey" -> new ApiError("INVALID_TOUR_REVIEWER",
                            "Tour reviewer does not exist", Map.of("reviewedBy", "Tour must have an existing reviewer"));
                    default -> null;
                };
                if (error != null) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
                }
            }
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("TOUR_DATA_CONFLICT", "Tour or schedule data conflicts with existing records"));
    }

    private static String expectedValue(Class<?> type) {
        if (type != null && type.isEnum()) {
            return "Must be one of: " + Arrays.stream(type.getEnumConstants())
                    .map(value -> ((Enum<?>) value).name()).collect(Collectors.joining(", ")) + " (case-sensitive)";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "Must be a whole number";
        }
        if (type == OffsetDateTime.class) {
            return "Must be a date and time with timezone, for example 2030-01-10T07:00:00+07:00";
        }
        if (type == LocalTime.class) {
            return "Must be a time, for example 07:30:00";
        }
        return "Value has the wrong type or format";
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> unavailable(DataAccessException ex) {
        log.error("Tour database operation failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("TOUR_UNAVAILABLE", "Tour service temporarily unavailable"));
    }
}
