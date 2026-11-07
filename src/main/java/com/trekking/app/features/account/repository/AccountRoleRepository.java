package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AccountRoleId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRoleRepository extends JpaRepository<AccountRole, AccountRoleId> {
    List<AccountRole> findAllByAccountId(Long accountId);

    // Raw role text so a leftover archived label cannot fail enum mapping for the whole page.
    @Query(value = "select account_id, role from trekking.account_roles where account_id in (:ids)", nativeQuery = true)
    List<Object[]> findRoleLabelsByAccountIds(@Param("ids") Collection<Long> ids);
}
