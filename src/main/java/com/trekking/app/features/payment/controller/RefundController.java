package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.request.RefundApproveRequest;
import com.trekking.app.features.payment.dto.request.RefundManualTransferRequest;
import com.trekking.app.features.payment.dto.request.RefundRejectRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundPreviewResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import com.trekking.app.features.payment.service.RefundService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) {
        this.refunds = refunds;
    }

    @GetMapping("/bookings/{bookingId}/refund-preview")
    public RefundPreviewResponse preview(@PathVariable long bookingId) {
        return refunds.previewForCustomer(bookingId);
    }

    @GetMapping("/bookings/{bookingId}/refund")
    public ResponseEntity<RefundResponse> customerRefund(@PathVariable long bookingId) {
        RefundResponse refund = refunds.latestForCustomer(bookingId);
        return refund == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(refund);
    }

    @GetMapping("/admin/refunds")
    public Page<RefundResponse> list(@RequestParam(required = false) String status,
                                     @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable) {
        return refunds.list(status, pageable);
    }

    @GetMapping("/admin/refunds/{refundId}")
    public RefundDetailResponse detail(@PathVariable long refundId) {
        return refunds.detail(refundId);
    }

    @PostMapping("/admin/refunds/{refundId}/approve")
    public RefundResponse approve(@PathVariable long refundId,
                                  @Valid @RequestBody(required = false) RefundApproveRequest request) {
        return refunds.approve(refundId, request);
    }

    @PostMapping("/admin/refunds/{refundId}/reject")
    public RefundResponse reject(@PathVariable long refundId, @Valid @RequestBody RefundRejectRequest request) {
        return refunds.reject(refundId, request);
    }

    @PostMapping("/admin/refunds/{refundId}/manual-transfer")
    public RefundResponse confirmManualTransfer(@PathVariable long refundId,
                                                @Valid @RequestBody RefundManualTransferRequest request) {
        return refunds.confirmManualTransfer(refundId, request);
    }

    @PostMapping("/admin/refunds/{refundId}/payos-payout")
    public RefundResponse startPayOsPayout(@PathVariable long refundId) {
        return refunds.startPayOsPayout(refundId);
    }

    @PostMapping("/admin/refunds/{refundId}/payos-sync")
    public RefundResponse syncPayOsPayout(@PathVariable long refundId) {
        return refunds.syncPayOsPayout(refundId);
    }
}
