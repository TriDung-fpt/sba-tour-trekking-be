package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.tour.enums.MediaType;

public record TourMediaResponse(
        Long id, MediaType mediaType, String url, String caption, Integer displayOrder, Boolean isCover) {
}
