package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.TourSchedule;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourScheduleRepository extends JpaRepository<TourSchedule, Long> {
    List<TourSchedule> findAllByTourIdAndStatusAndStartsAtGreaterThanEqualOrderByStartsAtAsc(
            Long tourId, ScheduleStatus status, OffsetDateTime now);

    @Query("select min(s.basePriceVnd) from TourSchedule s where s.tourId = :tourId and s.status = :status and s.startsAt >= :now and s.capacity > s.heldSeats + s.confirmedSeats")
    Optional<Long> findStartingPrice(@Param("tourId") Long tourId, @Param("status") ScheduleStatus status,
                                    @Param("now") OffsetDateTime now);

    Optional<TourSchedule> findByIdAndTourIdAndCompanyId(Long id, Long tourId, Long companyId);

    List<TourSchedule> findAllByTourIdOrderByStartsAtAsc(Long tourId);
}
