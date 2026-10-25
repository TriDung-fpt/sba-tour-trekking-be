package com.trekking.app.features.tour.dto.response;

public record EquipmentCatalogResponse(long id, String name, String description, Long individualRentalPriceVnd,
                                       String imageUrl) {}
