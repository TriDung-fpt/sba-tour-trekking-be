package com.trekking.app.features.policy.dto.response;

import java.time.OffsetDateTime;

public record PublicPolicyResponse(String code, String title, String content,
                                   int versionNumber, OffsetDateTime effectiveFrom) {}
