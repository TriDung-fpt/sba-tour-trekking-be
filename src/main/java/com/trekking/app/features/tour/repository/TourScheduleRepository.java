package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourSchedule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.OffsetDateTime;
import com.trekking.app.features.tour.enums.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourScheduleRepository extends JpaRepository<TourSchedule, Long> {
    Page<TourSchedule> findAllByTourId(long tourId, Pageable pageable);
    Page<TourSchedule> findAllByTourIdAndStatusAndStartsAtAfterOrderByStartsAtAscIdAsc(
            long tourId, ScheduleStatus status, OffsetDateTime now, Pageable pageable);
    Optional<TourSchedule> findByIdAndTourId(long id, long tourId);
    boolean existsByTourId(long tourId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TourSchedule s where s.id = :id and s.tourId = :tourId")
    Optional<TourSchedule> findLockedByIdAndTourId(@Param("id") long id, @Param("tourId") long tourId);
}
