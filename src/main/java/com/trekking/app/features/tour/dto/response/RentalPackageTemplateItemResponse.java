package com.trekking.app.features.tour.dto.response;

public record RentalPackageTemplateItemResponse(long equipmentId, String name, String description,
                                                 int quantityInPackage, Long individualRentalPriceVnd) {}
