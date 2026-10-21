package com.trekking.app.features.tour.dto.response;

public record TourManagementMedia(long id, String mediaType, String url, String caption, int displayOrder, boolean cover) {}
