package com.trekking.app.features.company.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record CompanyApplicationQueueItem(long companyId, long accountId, String businessName, String taxCode,
                                          String businessType, Integer foundedYear, String address,
                                          String contactName, String email, String phone, String description,
                                          List<String> tourRegions, OffsetDateTime safetyRulesAcceptedAt,
                                          OffsetDateTime submittedAt) {}
