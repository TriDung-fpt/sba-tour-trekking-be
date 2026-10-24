package com.trekking.app.features.account.service;

import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;

public interface AccountAdministrationService {
    AdminAccountResponse get(Long id);
    AdminAccountResponse block(Long id, String reason);
    AdminAccountResponse unblock(Long id, String reason);
    AdminAccountResponse replaceRoles(Long id, AccountAdministrationRequests.Roles request);
}
