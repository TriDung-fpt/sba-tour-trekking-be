package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourActivity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourActivityRepository extends JpaRepository<TourActivity, Long> {
    List<TourActivity> findAllByTourIdOrderByDayNumberAscDisplayOrderAscIdAsc(long tourId);
    Optional<TourActivity> findByIdAndTourId(long activityId, long tourId);
}
