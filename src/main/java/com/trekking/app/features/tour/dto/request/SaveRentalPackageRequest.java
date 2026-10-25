package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveRentalPackageRequest(
        @NotBlank @Size(max = 200) String title,
        String sharedItems,
        String personalItemsToBring,
        @NotNull @PositiveOrZero Long rentalPriceVnd,
        @NotNull @Valid List<PackageItem> items) {
    public record PackageItem(
            @NotNull @Positive Long equipmentId,
            @NotNull @Positive Integer quantityInPackage,
            @PositiveOrZero Long individualRentalPriceVnd) {}
}
