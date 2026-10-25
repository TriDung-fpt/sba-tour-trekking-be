package com.trekking.app.features.storage;

public class ImageStorageException extends RuntimeException {
    public enum Reason { INVALID_IMAGE, FILE_TOO_LARGE, NOT_CONFIGURED, UNAVAILABLE }

    private final Reason reason;

    public ImageStorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
