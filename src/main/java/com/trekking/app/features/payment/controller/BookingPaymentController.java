package com.trekking.app.features.payment.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.payment.dto.request.CreatePaymentLinkRequest;
import com.trekking.app.features.payment.dto.response.BookingPaymentStatus;
import com.trekking.app.features.payment.dto.response.PaymentLinkResponse;
import com.trekking.app.features.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingPaymentController {
    private final PaymentService service;
    public BookingPaymentController(PaymentService service) { this.service = service; }

    @PostMapping("/payment-links")
    public PaymentLinkResponse createLink(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookingId, @Valid @RequestBody CreatePaymentLinkRequest request) {
        return service.createLink(user.accountId(), bookingId, request);
    }

    @GetMapping("/payment-status")
    public BookingPaymentStatus status(@AuthenticationPrincipal UserPrincipal user, @PathVariable long bookingId) {
        return service.status(user.accountId(), bookingId);
    }
}
