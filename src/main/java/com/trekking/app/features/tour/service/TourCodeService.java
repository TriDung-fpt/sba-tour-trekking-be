package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.CreateTourCodeRequest;
import com.trekking.app.features.tour.dto.request.DeleteTourCodeRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourCodeRequest;
import com.trekking.app.features.tour.dto.response.TourCodeLogResponse;
import com.trekking.app.features.tour.dto.response.TourCodeResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Admin-managed tour codes. A tour has at most one active code; deleted codes are kept and never reused.
 * Every change is written to audit_logs (entity_type = tour_code).
 */
public interface TourCodeService {
    Page<TourCodeResponse> list(Long tourId, boolean includeDeleted, String keyword, Pageable pageable);

    TourCodeResponse get(long id);

    TourCodeResponse create(CreateTourCodeRequest request);

    TourCodeResponse rename(long id, UpdateTourCodeRequest request);

    TourCodeResponse delete(long id, DeleteTourCodeRequest request);

    List<TourCodeLogResponse> logs(long tourId);

    /** Active code used as booking-code prefix; no authorization check (internal use). */
    Optional<String> activeCode(long tourId);
}
