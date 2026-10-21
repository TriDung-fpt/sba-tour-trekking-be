package com.trekking.app.core.common.validation;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EvidenceUrl {
    private EvidenceUrl() {}

    public static String require(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EVIDENCE_URL: Evidence must be an absolute HTTP(S) URL");
        }
    }
}
