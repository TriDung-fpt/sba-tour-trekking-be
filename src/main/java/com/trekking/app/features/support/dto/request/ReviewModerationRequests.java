package com.trekking.app.features.support.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ReviewModerationRequests {
    private ReviewModerationRequests() {}

    public record Reason(@NotBlank @Size(max = 2000) String reason) {
        public Reason { reason = reason == null ? null : reason.strip(); }
    }
}
