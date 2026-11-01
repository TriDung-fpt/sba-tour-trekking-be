package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record RentalPackageResponse(
        Long id,
        Long packageTemplateId,
        String title,
        String includedItems,
        Long rentalPriceVnd,
        boolean needsReview,
        List<RentalItemResponse> items) {

    public RentalPackageResponse(
            Long id,
            Long packageTemplateId,
            String title,
            String includedItems,
            Long rentalPriceVnd,
            List<RentalItemResponse> items) {
        this(id, packageTemplateId, title, includedItems, rentalPriceVnd, false, items == null ? List.of() : List.copyOf(items));
    }

    public RentalPackageResponse(Long id, Long packageTemplateId, String title, String includedItems, Long rentalPriceVnd) {
        this(id, packageTemplateId, title, includedItems, rentalPriceVnd, false, List.of());
    }
}
