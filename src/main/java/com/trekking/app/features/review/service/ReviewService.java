package com.trekking.app.features.review.service;

import com.trekking.app.features.review.dto.request.CreateReviewRequest;
import com.trekking.app.features.review.dto.response.AdminReviewDetailResponse;
import com.trekking.app.features.review.dto.response.AdminReviewPageResponse;
import com.trekking.app.features.review.dto.response.CustomerReviewResponse;
import com.trekking.app.features.review.dto.response.TourReviewPageResponse;

public interface ReviewService {
    AdminReviewPageResponse listAdmin(int page, int size, String query, String status, Integer rating,
                                      Long tourId, String createdFrom, String createdTo, String sort);

    AdminReviewDetailResponse getAdmin(long id);

    AdminReviewDetailResponse hide(long id, String reason);

    AdminReviewDetailResponse restore(long id, String reason);

    CustomerReviewResponse create(long bookingId, CreateReviewRequest request);

    TourReviewPageResponse listByTour(long tourId, int page, int size);
}
