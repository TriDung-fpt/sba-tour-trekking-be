package com.trekking.app.features.tour.dto.response;

public record RentalPackageResponse(Long id, String title, String includedItems, String sharedItems,
                                    String personalItemsToBring, Long rentalPriceVnd) {}
