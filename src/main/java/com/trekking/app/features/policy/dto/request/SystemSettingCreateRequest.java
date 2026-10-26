package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record SystemSettingCreateRequest(@NotBlank @Size(max = 100) String settingKey,
                                         @NotBlank @Size(max = 4000) String value,
                                         @NotNull OffsetDateTime effectiveFrom,
                                         @NotBlank @Size(max = 2000) String reason) {
    public SystemSettingCreateRequest {
        settingKey = settingKey == null ? null : settingKey.strip();
        value = value == null ? null : value.strip();
        reason = reason == null ? null : reason.strip();
    }
}
