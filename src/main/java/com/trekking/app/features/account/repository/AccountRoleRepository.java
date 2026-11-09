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

    @Query("""
            select r.accountId as accountId,
                   str(r.role) as roleLabel
            from AccountRole r
            where r.accountId in :ids
            """)
    List<AccountRoleLabel> findRoleLabelsByAccountIds(@Param("ids") Collection<Long> ids);

    interface AccountRoleLabel {
        Long getAccountId();
        String getRoleLabel();
    }
}
