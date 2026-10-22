package com.trekking.app.features.account.dto.response;

import java.util.List;

public record AuthorizationResponse(long accountId, List<String> roles, List<String> permissions,
                                    List<CompanyContext> companies) {
    public AuthorizationResponse {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
        companies = List.copyOf(companies);
    }

    public record CompanyContext(long companyId, String status, String memberRole) {}
}
