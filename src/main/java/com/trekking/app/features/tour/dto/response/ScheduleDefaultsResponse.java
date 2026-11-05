package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;

public record ScheduleDefaultsResponse(Integer capacity, Long basePriceVnd, Long equipmentFeeVnd,
        BigDecimal depositPercent, String meetingPoint, String contactPhone) {}
