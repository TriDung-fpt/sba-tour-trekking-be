package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AccountRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {
    List<AccountRole> findAllByAccountId(Long accountId);
}
