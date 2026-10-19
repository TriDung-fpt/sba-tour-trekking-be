package com.trekking.app.features.company.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgreementReceiptResponse(long id, long companyId, long policyVersionId,
                                       long acceptedBy, OffsetDateTime acceptedAt, String acceptedIp,
                                       OffsetDateTime emailVerifiedAt, BigDecimal platformFeePercent,
                                       int payoutWaitHours, String payoutTermsSnapshot,
                                       int versionNumber, String policyContent) {}
