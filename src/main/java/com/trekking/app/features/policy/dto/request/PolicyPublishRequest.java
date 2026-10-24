package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record PolicyPublishRequest(@NotBlank @Size(max = 100000) String content,
                                   @NotNull OffsetDateTime effectiveFrom) {}
