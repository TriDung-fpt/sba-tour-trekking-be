package com.trekking.app.features.account.service;

import com.trekking.app.features.account.dto.request.FinancialPermissionsRequest;
import com.trekking.app.features.account.dto.response.FinancialPermissionsResponse;

public interface FinancialPermissionService {
    FinancialPermissionsResponse get(long accountId);
    FinancialPermissionsResponse replace(long accountId, FinancialPermissionsRequest request);
}
