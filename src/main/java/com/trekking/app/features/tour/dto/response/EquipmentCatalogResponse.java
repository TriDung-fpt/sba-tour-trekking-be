package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.EquipmentStatus;

public record EquipmentCatalogResponse(long id, String name, String description, Long individualRentalPriceVnd,
                                       String imageUrl, EquipmentStatus status) {}
