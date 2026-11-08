package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.Guide;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    Optional<Guide> findByAccountId(Long accountId);

    Optional<Guide> findByAccountIdAndActiveTrue(Long accountId);

    @Query("select a.id from AppAccount a join com.trekking.app.features.account.entity.AccountRole r on r.accountId = a.id where r.role = com.trekking.app.features.account.enums.RoleType.GUIDE and a.status = com.trekking.app.features.account.enums.AccountStatus.ACTIVE and not exists (select 1 from Guide g where g.accountId = a.id)")
    List<Long> findActiveGuideAccountIdsWithoutProfile();

    @Query("select g from Guide g join fetch g.account a join com.trekking.app.features.account.entity.AccountRole r on r.accountId = a.id where g.active = true and r.role = com.trekking.app.features.account.enums.RoleType.GUIDE order by a.fullName, g.id")
    List<Guide> findActiveGuides();
}
