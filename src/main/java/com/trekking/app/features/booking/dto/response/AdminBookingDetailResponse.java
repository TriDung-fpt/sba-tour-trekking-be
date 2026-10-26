package com.trekking.app.features.booking.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminBookingDetailResponse(Long id, String bookingCode, Long customerId, Long scheduleId,
                                         String status, Integer participantCount, boolean includesEquipment,
                                         Long basePriceVnd, Long equipmentFeeVnd, Long totalPriceVnd,
                                         BigDecimal depositPercent, OffsetDateTime holdExpiresAt,
                                         OffsetDateTime balanceDueAt, OffsetDateTime cancelledAt,
                                         String cancellationReason, Long cancelledBy,
                                         BigDecimal acceptedPaymentTotalVnd, boolean hasReview,
                                         String customerName, String customerEmail, OffsetDateTime createdAt) {
}
