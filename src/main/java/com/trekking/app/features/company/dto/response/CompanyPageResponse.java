package com.trekking.app.features.company.dto.response;

import java.util.List;

public record CompanyPageResponse(List<CompanyResponse> items, int page, int size,
                                  long totalElements, int totalPages) {}
