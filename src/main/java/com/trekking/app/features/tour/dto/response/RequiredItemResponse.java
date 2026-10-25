package com.trekking.app.features.tour.dto.response;

public record RequiredItemResponse(Long id, Long equipmentId, String name, Boolean isRequired,
                                   String note, Integer displayOrder) {}
