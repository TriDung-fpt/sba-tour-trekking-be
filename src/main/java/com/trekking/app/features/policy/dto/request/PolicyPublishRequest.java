package com.trekking.app.features.policy.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/** {@code refundTiers} is only accepted for cancellation policies. */
public record PolicyPublishRequest(@NotBlank @Size(max = 100000) String content,
                                   @NotNull OffsetDateTime effectiveFrom,
                                   @Size(max = 20) List<@NotNull @Valid RefundTierRequest> refundTiers) {
    public PolicyPublishRequest(String content, OffsetDateTime effectiveFrom) {
        this(content, effectiveFrom, null);
    }
}
