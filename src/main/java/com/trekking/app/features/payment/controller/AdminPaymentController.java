package com.trekking.app.features.payment.controller;

import com.trekking.app.features.payment.dto.response.AdminPaymentAttemptResponse;
import com.trekking.app.features.payment.dto.response.AdminPaymentResponse;
import com.trekking.app.features.payment.dto.response.AdminRefundResponse;
import com.trekking.app.features.payment.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@SecurityRequirement(name = "bearerAuth")
public class AdminPaymentController {
    private final AdminPaymentService service;

    public AdminPaymentController(AdminPaymentService service) { this.service = service; }

    @GetMapping("/attempts")
    public Page<AdminPaymentAttemptResponse> attempts(@RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long bookingId) {
        return service.listAttempts(status, bookingId, page, size);
    }

    @GetMapping
    public Page<AdminPaymentResponse> payments(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String allocation,
                                               @RequestParam(required = false) Long bookingId) {
        return service.listPayments(allocation, bookingId, page, size);
    }

    @GetMapping("/refunds")
    public Page<AdminRefundResponse> refunds(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) Long bookingId) {
        return service.listRefunds(bookingId, page, size);
    }
}
