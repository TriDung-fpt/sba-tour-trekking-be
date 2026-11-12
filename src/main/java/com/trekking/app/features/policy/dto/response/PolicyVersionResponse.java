package com.trekking.app.features.policy.dto.response;

import com.trekking.app.features.policy.dto.request.RefundTierRequest;
import java.time.OffsetDateTime;
import java.util.List;

public record PolicyVersionResponse(long id, long policyId, String policyCode, String title,
                                    int versionNumber, String content, OffsetDateTime effectiveFrom,
                                    Long approvedBy, OffsetDateTime approvedAt,
                                    OffsetDateTime revokedAt, String revocationReason, String kind,
                                    List<RefundTierRequest> refundTiers) {}
