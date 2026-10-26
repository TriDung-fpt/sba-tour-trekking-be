package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.response.AdminPaymentAttemptResponse;
import com.trekking.app.features.payment.dto.response.AdminPaymentResponse;
import com.trekking.app.features.payment.dto.response.AdminRefundResponse;
import org.springframework.data.domain.Page;

public interface AdminPaymentService {
    Page<AdminPaymentAttemptResponse> listAttempts(String status, Long bookingId, int page, int size);
    Page<AdminPaymentResponse> listPayments(String allocation, Long bookingId, int page, int size);
    Page<AdminRefundResponse> listRefunds(Long bookingId, int page, int size);
}
