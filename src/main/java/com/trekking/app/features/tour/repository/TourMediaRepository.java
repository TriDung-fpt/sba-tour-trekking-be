package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;



import com.trekking.app.features.tour.entity.TourMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourMediaRepository extends JpaRepository<TourMedia, Long> {
    List<TourMedia> findAllByTourIdOrderByDisplayOrderAscIdAsc(Long tourId);
    void deleteAllByTourId(Long tourId);
}
