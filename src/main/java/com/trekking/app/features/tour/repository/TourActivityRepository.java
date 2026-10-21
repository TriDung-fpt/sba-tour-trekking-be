package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.TourActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourActivityRepository extends JpaRepository<TourActivity, Long> {
    List<TourActivity> findAllByTourIdOrderByDayNumberAscDisplayOrderAsc(Long tourId);
    void deleteAllByTourId(Long tourId);
}
