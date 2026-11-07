package com.trekking.app.features.equipment.service;

import com.trekking.app.features.equipment.dto.request.EquipmentHandoverRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;
import java.util.Optional;

public interface EquipmentHandoverService {
    /** The caller must authorize access to the booking before invoking this method. */
    Optional<EquipmentReservationResponse> findForAuthorizedBooking(long bookingId);
    EquipmentReservationResponse issue(long bookingId, EquipmentHandoverRequest request);
    EquipmentReservationResponse recordReturn(long bookingId, EquipmentHandoverRequest request);
    EquipmentReservationResponse read(long bookingId);
}
