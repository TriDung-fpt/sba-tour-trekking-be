package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourItineraryDay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourItineraryDayRepository extends JpaRepository<TourItineraryDay, Long> {
    List<TourItineraryDay> findAllByTourIdOrderByDayNumberAsc(long tourId);
    void deleteAllByTourId(long tourId);
}
