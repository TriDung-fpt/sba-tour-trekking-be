package com.trekking.app.features.account.service;

import com.trekking.app.features.account.dto.request.AccountAdministrationRequests;
import com.trekking.app.features.account.dto.response.AdminAccountResponse;
import com.trekking.app.features.account.dto.response.AuditLogResponse;
import org.springframework.data.domain.Page;

public interface AccountAdministrationService {
    AdminAccountResponse get(Long id);
    Page<AdminAccountResponse> list(String query, String role, String status, int page, int size);
    AdminAccountResponse update(Long id, AccountAdministrationRequests.Update request);
    AdminAccountResponse block(Long id, String reason);
    AdminAccountResponse unblock(Long id, String reason);
    AdminAccountResponse replaceRoles(Long id, AccountAdministrationRequests.Roles request);
    Page<AuditLogResponse> listAuditLogs(String entityType, Long entityId, String action, Long actorId,
                                         int page, int size);
}
