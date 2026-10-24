package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourMediaRepository extends JpaRepository<TourMedia, Long> {
    List<TourMedia> findAllByTourIdInAndIsCoverTrue(Collection<Long> tourIds);
    List<TourMedia> findAllByTourIdOrderByDisplayOrderAscIdAsc(long tourId);
}
