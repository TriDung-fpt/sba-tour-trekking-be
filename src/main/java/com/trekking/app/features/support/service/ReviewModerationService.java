package com.trekking.app.features.support.service;

import com.trekking.app.features.support.dto.response.ReviewResponse;
import org.springframework.data.domain.Page;

public interface ReviewModerationService {
    Page<ReviewResponse> list(String status, Long bookingId, int page, int size);
    ReviewResponse hide(Long id, String reason);
    ReviewResponse restore(Long id, String reason);
}
