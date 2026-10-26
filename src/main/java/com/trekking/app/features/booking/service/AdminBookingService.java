package com.trekking.app.features.booking.service;

import com.trekking.app.features.booking.dto.response.AdminBookingDetailResponse;
import com.trekking.app.features.booking.dto.response.AdminBookingSummaryResponse;
import org.springframework.data.domain.Page;

public interface AdminBookingService {
    Page<AdminBookingSummaryResponse> list(String status, String query, int page, int size);
    AdminBookingDetailResponse get(Long id);
}
