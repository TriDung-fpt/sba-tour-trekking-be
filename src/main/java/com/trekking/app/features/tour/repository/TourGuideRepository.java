package com.trekking.app.features.tour.repository;

import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.company.entity.Guide;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourGuideRepository extends JpaRepository<Guide, Long> {
    @Query("select g.id, g.companyId, g.account.fullName from Guide g where g.companyId in :companyIds and g.active = true and g.account.status = :active order by g.account.fullName")
    List<Object[]> findActiveGuideOptions(@Param("companyIds") Collection<Long> companyIds,
                                         @Param("active") AccountStatus active);
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
