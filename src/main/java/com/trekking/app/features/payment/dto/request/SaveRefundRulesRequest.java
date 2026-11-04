package com.trekking.app.features.payment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record SaveRefundRulesRequest(@NotNull OffsetDateTime effectiveFrom,
                                     @NotEmpty List<@Valid RefundRuleTierRequest> rules) {}
