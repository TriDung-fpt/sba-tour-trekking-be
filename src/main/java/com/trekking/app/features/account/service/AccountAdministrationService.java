package com.trekking.app.features.account.service;

import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.dto.response.AdminAccountPageResponse;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;
import com.trekking.app.features.account.dto.response.GuideOptionResponse;
import java.util.List;

public interface AccountAdministrationService {
    AdminAccountPageResponse list(int page, int size, String query, String role, String status, String sort);
    AdminAccountResponse get(Long id);
    AdminAccountResponse block(Long id, String reason);
    AdminAccountResponse unblock(Long id, String reason);
    AdminAccountResponse replaceRoles(Long id, AccountAdministrationRequests.Roles request);
    List<GuideOptionResponse> listGuides();
}
