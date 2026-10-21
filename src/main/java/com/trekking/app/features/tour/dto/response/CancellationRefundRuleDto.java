package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;

public record CancellationRefundRuleDto(int daysBeforeDeparture, BigDecimal refundPercent) {}
