package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;

public record LocationResponse(long id, String name, String provinceName, BigDecimal latitude, BigDecimal longitude) {}
