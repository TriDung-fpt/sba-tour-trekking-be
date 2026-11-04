package com.trekking.app.features.payment.dto.response;

import com.trekking.app.features.payment.dto.request.RefundRuleTierRequest;
import java.time.OffsetDateTime;
import java.util.List;

public record RefundRulesResponse(long versionId, OffsetDateTime effectiveFrom,
                                 List<RefundRuleTierRequest> rules) {}
