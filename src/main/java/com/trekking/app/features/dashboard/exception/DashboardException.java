package com.trekking.app.features.dashboard.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DashboardException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DashboardException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DashboardException invalidRange(String message) {
        return new DashboardException(HttpStatus.BAD_REQUEST, "DASHBOARD_INVALID_RANGE", message);
    }

    public static DashboardException rangeTooLarge() {
        return new DashboardException(HttpStatus.BAD_REQUEST, "DASHBOARD_RANGE_TOO_LARGE",
                "Requested range exceeds the maximum number of periods");
    }

    public static DashboardException invalidCompareYear() {
        return new DashboardException(HttpStatus.BAD_REQUEST, "DASHBOARD_INVALID_COMPARE_YEAR",
                "baseYear and compareYear must be different");
    }
}
