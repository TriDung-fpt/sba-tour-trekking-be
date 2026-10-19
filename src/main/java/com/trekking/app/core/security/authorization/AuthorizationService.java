package com.trekking.app.core.security.authorization;

import com.trekking.app.features.account.dto.response.AuthorizationResponse;
import com.trekking.app.features.account.enums.RoleType;
import java.util.Set;

public interface AuthorizationService {
    long currentAccountId();
    Set<RoleType> currentRoles();
    void require(Permission permission);
    void requireCompany(Permission permission, long companyId);
    void requireGuideSchedule(long scheduleId);
    AuthorizationResponse authorization();
}
