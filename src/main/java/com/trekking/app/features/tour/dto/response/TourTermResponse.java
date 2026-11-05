package com.trekking.app.features.tour.dto.response;
public record TourTermResponse(long versionId, String title, String content, int versionNumber, int displayOrder, boolean primaryCancellation) {}
