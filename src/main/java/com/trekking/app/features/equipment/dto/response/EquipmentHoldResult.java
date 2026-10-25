package com.trekking.app.features.equipment.dto.response;

public record EquipmentHoldResult(Long reservationId, boolean includesEquipment, long rentalFeeVnd,
                                  String equipmentSnapshot, String reservationStatus) {}
