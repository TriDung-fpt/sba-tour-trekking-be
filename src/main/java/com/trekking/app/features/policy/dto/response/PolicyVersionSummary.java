package com.trekking.app.features.policy.dto.response;

import java.time.OffsetDateTime;

public record PolicyVersionSummary(long policyId, int versionNumber, OffsetDateTime approvedAt,
                                   OffsetDateTime revokedAt) {}
