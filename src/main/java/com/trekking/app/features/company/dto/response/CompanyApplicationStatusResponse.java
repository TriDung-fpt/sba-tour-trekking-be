package com.trekking.app.features.company.dto.response;

public record CompanyApplicationStatusResponse(String status, Long companyId, String businessName,
                                               String decisionReason) {}
