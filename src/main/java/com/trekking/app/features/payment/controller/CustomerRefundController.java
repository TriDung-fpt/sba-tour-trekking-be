package com.trekking.app.features.payment.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.payment.dto.request.CreateRefundRequest;
import com.trekking.app.features.payment.dto.response.RefundActionResponse;
import com.trekking.app.features.payment.service.PaymentOperationsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/refund-requests")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRefundController {
    private final PaymentOperationsService operations;

    public CustomerRefundController(PaymentOperationsService operations) { this.operations = operations; }

    @PostMapping
    public RefundActionResponse request(@AuthenticationPrincipal UserPrincipal customer,
            @PathVariable long bookingId, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreateRefundRequest request) {
        return operations.requestRefund(customer.accountId(), bookingId, key, request);
    }
}
