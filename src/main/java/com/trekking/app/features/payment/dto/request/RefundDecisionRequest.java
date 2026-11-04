package com.trekking.app.features.payment.dto.request;

import jakarta.validation.constraints.Size;

public record RefundDecisionRequest(@Size(max = 1000) String note) {}
