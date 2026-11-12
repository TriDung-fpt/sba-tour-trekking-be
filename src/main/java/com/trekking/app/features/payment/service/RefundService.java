package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.RefundAccountRequest;
import com.trekking.app.features.payment.dto.request.RefundApproveRequest;
import com.trekking.app.features.payment.dto.request.RefundManualTransferRequest;
import com.trekking.app.features.payment.dto.request.RefundRejectRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundPreviewResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Refund lifecycle: requested (on cancellation, amount from the cancellation policy tiers)
 * -> approved / rejected by finance -> paid manually or via PayOS payout -> completed.
 * Money never moves automatically: every payout step is an explicit finance action.
 */
public interface RefundService {
    /** Called inside the booking cancellation transaction, after the booking row is locked and cancelled. */
    RefundResponse requestOnCancellation(long bookingId, long customerId, String reason, RefundAccountRequest account);

    /** Computes the refund the customer would receive if they cancelled now. */
    RefundPreviewResponse previewForCustomer(long bookingId);

    RefundResponse latestForCustomer(long bookingId);

    Page<RefundResponse> list(String status, Pageable pageable);

    RefundDetailResponse detail(long refundId);

    RefundResponse approve(long refundId, RefundApproveRequest request);

    RefundResponse reject(long refundId, RefundRejectRequest request);

    RefundResponse confirmManualTransfer(long refundId, RefundManualTransferRequest request);

    RefundResponse startPayOsPayout(long refundId);

    RefundResponse syncPayOsPayout(long refundId);
}
