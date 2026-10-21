package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourScheduleRepository extends JpaRepository<TourSchedule, Long> {
    List<TourSchedule> findAllByTourIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            Long tourId, ScheduleStatus status, OffsetDateTime now);

    @Query("select min(s.basePriceVnd) from TourSchedule s where s.tourId = :tourId and s.status = :status and s.startsAt >= :now and s.capacity > s.heldSeats + s.confirmedSeats")
    Optional<Long> findStartingPrice(@Param("tourId") Long tourId, @Param("status") ScheduleStatus status,
                                    @Param("now") OffsetDateTime now);

    Optional<TourSchedule> findByIdAndTourIdAndCompanyId(Long id, Long tourId, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TourSchedule s join fetch s.tour where s.id = :id")
    Optional<TourSchedule> findByIdForBooking(@Param("id") Long id);

    @Query(value = """
            select s from TourSchedule s join fetch s.tour t
            where s.companyId in :companyIds
              and (:status is null or s.status = :status)
              and (:fromDate is null or s.startsAt >= :fromDate)
              and (:toDate is null or s.startsAt < :toDate)
              and (:includeCancelled = true or s.status <> com.trekking.app.features.tour.enums.ScheduleStatus.CANCELLED)
            """, countQuery = """
            select count(s) from TourSchedule s
            where s.companyId in :companyIds
              and (:status is null or s.status = :status)
              and (:fromDate is null or s.startsAt >= :fromDate)
              and (:toDate is null or s.startsAt < :toDate)
              and (:includeCancelled = true or s.status <> com.trekking.app.features.tour.enums.ScheduleStatus.CANCELLED)
            """)
    Page<TourSchedule> findPartnerDepartures(@Param("companyIds") Collection<Long> companyIds,
            @Param("status") ScheduleStatus status, @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate, @Param("includeCancelled") boolean includeCancelled,
            Pageable pageable);

    List<TourSchedule> findAllByTourIdOrderByStartsAtAsc(Long tourId);
}
