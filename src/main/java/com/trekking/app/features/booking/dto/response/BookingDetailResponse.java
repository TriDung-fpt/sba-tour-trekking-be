package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.enums.BookingEquipmentChoice;
import com.trekking.app.features.booking.enums.BookingStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record BookingDetailResponse(
                Long id,
                String bookingCode,
                String qrPayload,
                BookingStatus status,
                Long customerId,
                Long scheduleId,
                Long tourId,
                String tourTitle,
                OffsetDateTime startsAt,
                OffsetDateTime endsAt,
                String meetingPoint,
                BookingEquipmentChoice equipmentChoice,
                Map<String, Object> equipmentSnapshot,
                Map<String, Object> cancellationPolicy,
                BookingPriceResponse price,
                List<BookingPassengerResponse> passengers,
                OffsetDateTime holdExpiresAt,
                OffsetDateTime balanceDueAt,
                OffsetDateTime priceLockedAt,
                OffsetDateTime cancelledAt,
                String cancellationReason,
                Long cancelledBy,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt) {
}
