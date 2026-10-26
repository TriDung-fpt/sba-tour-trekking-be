package com.trekking.app.features.booking.dto.response;

import java.time.OffsetDateTime;

public record AdminBookingSummaryResponse(Long id, String bookingCode, Long customerId, Long scheduleId,
                                          String status, Integer participantCount, Long basePriceVnd,
                                          Long equipmentFeeVnd, Long totalPriceVnd,
                                          OffsetDateTime holdExpiresAt, OffsetDateTime balanceDueAt,
                                          OffsetDateTime cancelledAt, String cancellationReason) {
}
