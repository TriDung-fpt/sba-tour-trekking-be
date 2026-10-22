package com.trekking.app.features.company.dto.response;

import java.time.OffsetDateTime;

public record CompanyResponse(long id, String name, String taxCode, String representativeName,
        String contactEmail, String contactPhone, String address, String description,
        String status, boolean licenseAvailable, String licenseUrl, Long approvedBy,
        OffsetDateTime approvedAt, String decisionReason, OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
