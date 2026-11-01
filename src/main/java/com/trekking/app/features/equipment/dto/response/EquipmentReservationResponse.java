package com.trekking.app.features.equipment.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record EquipmentReservationResponse(long reservationId, long bookingId, long scheduleId,
                                           Long packageId, String status, OffsetDateTime holdExpiresAt,
                                           List<Item> items) {
    public record Item(long equipmentId, String equipmentName, int quantity, int issuedQuantity,
                       int returnedQuantity) {}
}
