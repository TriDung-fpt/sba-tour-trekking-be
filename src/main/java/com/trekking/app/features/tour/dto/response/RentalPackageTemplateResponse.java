package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record RentalPackageTemplateResponse(long id, String title, long rentalPriceVnd, String imageUrl,
        List<RentalPackageTemplateItemResponse> items) {}
