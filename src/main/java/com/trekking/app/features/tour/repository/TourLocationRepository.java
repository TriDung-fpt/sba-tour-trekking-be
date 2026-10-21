package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.TourLocation;
import com.trekking.app.features.tour.entity.TourLocationId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourLocationRepository extends JpaRepository<TourLocation, TourLocationId> {
    List<TourLocation> findAllByTourIdOrderByVisitOrderAsc(Long tourId);
    void deleteAllByTourId(Long tourId);
}
