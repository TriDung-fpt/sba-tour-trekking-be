package com.trekking.app.features.equipment.service;

import com.trekking.app.features.equipment.dto.request.EquipmentHoldRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentHoldResult;

public interface EquipmentReservationService {
    EquipmentHoldResult hold(EquipmentHoldRequest request);
    void confirm(long bookingId);
    void release(long bookingId);
    int releaseExpiredHolds();
}
