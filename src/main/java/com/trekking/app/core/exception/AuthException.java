package com.trekking.app.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AuthException unavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE", "Authentication service temporarily unavailable");
    }

    public static AuthException invalidSession() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", "Session is invalid or expired");
    }
}
