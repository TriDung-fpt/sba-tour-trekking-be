package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourCode;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.Repository;

public interface TourCodeRepository extends Repository<TourCode, Long> {
    TourCode saveAndFlush(TourCode code);

    Optional<TourCode> findById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TourCode> findForUpdateById(long id);

    /** Codes are unique forever, including soft-deleted ones. */
    boolean existsByCode(String code);

    Optional<TourCode> findFirstByTourIdAndDeletedAtIsNull(long tourId);

    List<TourCode> findAllByTourIdOrderByCreatedAtDescIdDesc(long tourId);

    List<TourCode> findAllByTourIdInAndDeletedAtIsNull(Collection<Long> tourIds);

    // Admin list filters. An empty keyword matches every code.
    Page<TourCode> findAllByCodeContaining(String keyword, Pageable pageable);

    Page<TourCode> findAllByDeletedAtIsNullAndCodeContaining(String keyword, Pageable pageable);

    Page<TourCode> findAllByTourIdAndCodeContaining(long tourId, String keyword, Pageable pageable);

    Page<TourCode> findAllByTourIdAndDeletedAtIsNullAndCodeContaining(long tourId, String keyword, Pageable pageable);
}
