package com.trekking.app.features.payment.service;

import com.trekking.app.features.booking.entity.Booking;
import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.request.RefundSubmissionRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import java.util.List;

public interface RefundService {
    RefundResponse requestOnCancellation(Booking booking, long customerId, String reason, RefundSubmissionRequest request);
    RefundResponse forCustomerBooking(long bookingId);
    List<RefundResponse> listForFinance();
    RefundDetailResponse detailForFinance(long refundId);
    RefundResponse approve(long refundId);
    RefundResponse reject(long refundId, RefundDecisionRequest request);
}
