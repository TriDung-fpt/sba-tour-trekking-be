package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record TourEquipmentResponse(RentalPackageResponse equipmentPackage, List<RentalItemResponse> items) {}
