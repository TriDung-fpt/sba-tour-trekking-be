package com.trekking.app.features.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewModerationRequest(@NotBlank @Size(max = 2000) String reason) {
    public ReviewModerationRequest {
        reason = reason == null ? null : reason.strip();
    }
}
