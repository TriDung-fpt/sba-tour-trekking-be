package com.trekking.app.features.policy.dto.response;

import java.time.OffsetDateTime;

public record PolicyResponse(long id, String code, String title, String kind, boolean active,
                             Integer versionNumber, OffsetDateTime updatedAt) {}
