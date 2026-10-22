package com.trekking.app.features.policy.dto.response;

import java.time.OffsetDateTime;

public record PolicyVersionResponse(long id, long policyId, String policyCode, String title,
                                    int versionNumber, String content, OffsetDateTime effectiveFrom,
                                    Long approvedBy, OffsetDateTime approvedAt) {}
