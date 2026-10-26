package com.trekking.app.features.policy.dto.response;

import java.time.OffsetDateTime;

public record SystemSettingResponse(Long id, String settingKey, int versionNumber, String value,
                                    OffsetDateTime effectiveFrom, Long changedBy, String changeReason,
                                    OffsetDateTime createdAt) {
}
