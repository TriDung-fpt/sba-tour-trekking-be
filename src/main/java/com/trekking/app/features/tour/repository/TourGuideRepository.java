package com.trekking.app.features.tour.repository;

import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.company.entity.Guide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourGuideRepository extends JpaRepository<Guide, Long> {
    boolean existsByIdAndCompanyIdAndActiveTrueAndAccount_Status(Long guideId, Long companyId,
                                                                  AccountStatus accountStatus);

    @Query("""
            select g.id from Guide g join g.account a
            where g.accountId = :accountId and g.companyId = :companyId
              and g.active = true and a.status = :active
            """)
    Optional<Long> findActiveGuideId(@Param("accountId") Long accountId, @Param("companyId") Long companyId,
                                     @Param("active") AccountStatus active);
}
