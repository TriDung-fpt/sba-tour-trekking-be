package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record PagedResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}
