package com.trekking.app.features.account.repository;

import com.trekking.app.features.account.entity.Guide;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {
    Optional<Guide> findByAccountIdAndActiveTrue(Long accountId);

    @Query("select g from Guide g join fetch g.account a join com.trekking.app.features.account.entity.AccountRole r on r.accountId = a.id where g.active = true and r.role = com.trekking.app.features.account.enums.RoleType.GUIDE order by a.fullName, g.id")
    List<Guide> findActiveGuides();
}
