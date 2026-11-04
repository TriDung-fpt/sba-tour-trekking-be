package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.request.RefundDecisionRequest;
import com.trekking.app.features.payment.dto.response.RefundDetailResponse;
import com.trekking.app.features.payment.dto.response.RefundResponse;
import com.trekking.app.features.payment.service.RefundService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {
    private final RefundService refunds;

    public RefundController(RefundService refunds) { this.refunds = refunds; }

    @GetMapping("/bookings/{bookingId}/refund")
    public RefundResponse customerRefund(@PathVariable long bookingId) {
        return refunds.forCustomerBooking(bookingId);
    }

    @GetMapping("/admin/refunds")
    public List<RefundResponse> listForFinance() { return refunds.listForFinance(); }

    @GetMapping("/admin/refunds/{refundId}")
    public RefundDetailResponse detail(@PathVariable long refundId) { return refunds.detailForFinance(refundId); }

    @PostMapping("/admin/refunds/{refundId}/approve")
    public RefundResponse approve(@PathVariable long refundId) { return refunds.approve(refundId); }

    @PostMapping("/admin/refunds/{refundId}/reject")
    @ResponseStatus(HttpStatus.OK)
    public RefundResponse reject(@PathVariable long refundId, @Valid @RequestBody RefundDecisionRequest request) {
        return refunds.reject(refundId, request);
    }
}
