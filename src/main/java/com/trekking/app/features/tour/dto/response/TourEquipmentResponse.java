package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record TourEquipmentResponse(
        Long version,
        boolean needsReview,
        List<RentalPackageResponse> packages,
        List<RentalItemResponse> individualItems,
        List<RentalItemResponse> items,
        RentalPackageResponse equipmentPackage) {

    public TourEquipmentResponse(
            Long version,
            List<RentalPackageResponse> packages,
            List<RentalItemResponse> individualItems) {
        this(
                version == null ? 0L : version,
                packages != null && packages.stream().anyMatch(RentalPackageResponse::needsReview),
                packages == null ? List.of() : List.copyOf(packages),
                individualItems == null ? List.of() : List.copyOf(individualItems),
                individualItems == null ? List.of() : List.copyOf(individualItems),
                packages == null || packages.isEmpty() ? null : packages.getFirst());
    }

    public TourEquipmentResponse(List<RentalPackageResponse> packages, List<RentalItemResponse> items) {
        this(0L, packages, items);
    }

    public TourEquipmentResponse(RentalPackageResponse equipmentPackage, List<RentalItemResponse> items) {
        this(
                0L,
                equipmentPackage == null ? List.of() : List.of(equipmentPackage),
                items == null ? List.of() : List.copyOf(items));
    }
}
