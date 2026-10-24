package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourMedia;
import com.trekking.app.features.tour.enums.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourMediaRepository extends JpaRepository<TourMedia, Long> {
    List<TourMedia> findAllByTourIdInAndIsCoverTrue(Collection<Long> tourIds);
    List<TourMedia> findAllByTourIdOrderByDisplayOrderAscIdAsc(long tourId);
    Optional<TourMedia> findByIdAndTourIdAndMediaType(long mediaId, long tourId, MediaType mediaType);
    boolean existsByTourIdAndCloudinaryPublicIdIsNotNull(long tourId);
}
