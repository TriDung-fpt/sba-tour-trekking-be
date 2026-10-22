package com.trekking.app.features.account.repository;

import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.account.entity.AccountFinancialPermission;
import com.trekking.app.features.account.entity.AccountFinancialPermissionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountFinancialPermissionRepository
        extends JpaRepository<AccountFinancialPermission, AccountFinancialPermissionId> {
    List<AccountFinancialPermission> findAllByAccountId(Long accountId);
    boolean existsByAccountIdAndPermission(Long accountId, Permission permission);
}
