package com.trekking.app.features.account.dto.response;

import java.util.List;

public record FinancialPermissionsResponse(long accountId, List<String> permissions) {
    public FinancialPermissionsResponse { permissions = List.copyOf(permissions); }
}
