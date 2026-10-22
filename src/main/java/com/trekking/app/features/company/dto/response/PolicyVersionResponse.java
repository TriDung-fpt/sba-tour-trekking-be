package com.trekking.app.features.company.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PolicyVersionResponse(long id, long policyId, String policyCode, String title,
                                    int versionNumber, String content, OffsetDateTime effectiveFrom,
                                    long approvedBy, OffsetDateTime approvedAt, BigDecimal platformFeePercent,
                                    int payoutWaitHours, String payoutTermsSnapshot) {}
