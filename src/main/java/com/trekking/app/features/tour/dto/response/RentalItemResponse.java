package com.trekking.app.features.tour.dto.response;

public record RentalItemResponse(Long id, Long equipmentId, String name, String description,
                                 Integer quantityInPackage, Long individualRentalPriceVnd) {}
