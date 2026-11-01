package com.trekking.app.features.booking.repository;

import com.trekking.app.features.tour.entity.TourSchedule;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Booking-owned access to schedule seat counters; Tour administration stays in the tour feature. */
public interface BookingScheduleRepository extends Repository<TourSchedule, Long> {
    Optional<TourSchedule> findById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TourSchedule s where s.id = :id")
    Optional<TourSchedule> findForUpdate(@Param("id") long id);
}
