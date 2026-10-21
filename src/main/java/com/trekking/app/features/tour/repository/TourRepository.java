package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import com.trekking.app.features.company.enums.CompanyStatus;
import com.trekking.app.features.company.enums.CompanyMemberRole;
import com.trekking.app.features.company.entity.CompanyMember;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.List;

public interface TourRepository extends JpaRepository<Tour, Long> {
    @Query("""
            select t.companyId from Tour t, CompanyMember cm
            where t.id = :tourId and cm.companyId = t.companyId and cm.accountId = :accountId
              and t.company.status = :approved
              and cm.active = true
              and (:managerOnly = false or cm.memberRole in :managerRoles)
            """)
    Optional<Long> findActiveMemberCompanyForTour(@Param("tourId") Long tourId,
                                                  @Param("accountId") Long accountId,
                                                  @Param("managerOnly") boolean managerOnly,
                                                  @Param("managerRoles") List<CompanyMemberRole> managerRoles,
                                                  @Param("approved") CompanyStatus approved);

    @Query("""
            select t from Tour t
            where t.status = :published and t.company.status = :approved
              and (:query = '' or lower(t.title) like concat('%', lower(:query), '%')
                   or lower(coalesce(t.description, '')) like concat('%', lower(:query), '%'))
              and (:difficulty is null or t.difficulty = :difficulty)
              and (:destinationId <= 0 or exists (select tl.tourId from TourLocation tl
                   where tl.tourId = t.id and tl.locationId = :destinationId))
              and (:equipmentPackageId <= 0 or exists (select ep.id from TourEquipmentPackage ep
                   where ep.tourId = t.id and ep.id = :equipmentPackageId))
              and coalesce((select avg(r.tourRating) from Review r
                   where r.booking.schedule.tourId = t.id and r.status = :visible), 0) >= :minRating
              and exists (select s.id from TourSchedule s where s.tourId = t.id and s.status = :scheduled
                   and s.startsAt >= :now and s.startsAt >= :dateFrom
                   and s.startsAt <= :dateTo and s.capacity > s.heldSeats + s.confirmedSeats
                   and s.basePriceVnd >= :minPrice
                   and s.basePriceVnd <= :maxPrice)
            order by
              case when :sort = 'price_asc' then (select min(s.basePriceVnd) from TourSchedule s where s.tourId=t.id and s.status=:scheduled and s.startsAt>=:now and s.capacity>s.heldSeats+s.confirmedSeats) end asc nulls last,
              case when :sort = 'price_desc' then (select min(s.basePriceVnd) from TourSchedule s where s.tourId=t.id and s.status=:scheduled and s.startsAt>=:now and s.capacity>s.heldSeats+s.confirmedSeats) end desc nulls last,
              case when :sort = 'rating_desc' then (select avg(r.tourRating) from Review r where r.booking.schedule.tourId=t.id and r.status=:visible) end desc nulls last,
              case when :sort = 'departure_asc' then (select min(s.startsAt) from TourSchedule s where s.tourId=t.id and s.status=:scheduled and s.startsAt>=:now and s.capacity>s.heldSeats+s.confirmedSeats) end asc nulls last,
              t.createdAt desc, t.id desc
            """)
    Page<Tour> searchPublished(@Param("published") TourStatus published, @Param("approved") CompanyStatus approved,
                               @Param("scheduled") ScheduleStatus scheduled,
                               @Param("query") String query, @Param("destinationId") Long destinationId,
                               @Param("difficulty") DifficultyLevel difficulty, @Param("dateFrom") OffsetDateTime dateFrom,
                               @Param("dateTo") OffsetDateTime dateTo, @Param("minPrice") Long minPrice,
                               @Param("maxPrice") Long maxPrice, @Param("minRating") BigDecimal minRating,
                               @Param("equipmentPackageId") Long equipmentPackageId, @Param("now") OffsetDateTime now,
                               @Param("sort") String sort,
                               @Param("visible") com.trekking.app.features.support.enums.ReviewStatus visible,
                               Pageable pageable);

    Optional<Tour> findByIdAndStatusAndCompany_Status(Long id, TourStatus status, CompanyStatus companyStatus);

    Optional<Tour> findByIdAndCompanyId(Long id, Long companyId);

    Page<Tour> findAllByCompanyId(Long companyId, Pageable pageable);

    Page<Tour> findAllByCompanyIdAndStatus(Long companyId, TourStatus status, Pageable pageable);

    Page<Tour> findAllByStatus(TourStatus status, Pageable pageable);

    Optional<Tour> findByIdAndStatus(Long id, TourStatus status);
}
