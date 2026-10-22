package com.trekking.app.features.company.dto.response;

public record PolicyResponse(long id, String code, String title, String kind, Long companyId, boolean active) {}
