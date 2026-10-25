package com.trekking.app.features.equipment.service;

import com.trekking.app.features.equipment.dto.request.EquipmentHandoverRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;

public interface EquipmentHandoverService {
    EquipmentReservationResponse issue(long bookingId, EquipmentHandoverRequest request);
    EquipmentReservationResponse recordReturn(long bookingId, EquipmentHandoverRequest request);
    EquipmentReservationResponse read(long bookingId);
}
