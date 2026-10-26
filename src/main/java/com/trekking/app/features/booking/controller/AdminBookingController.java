package com.trekking.app.features.booking.controller;

import com.trekking.app.features.booking.dto.response.AdminBookingDetailResponse;
import com.trekking.app.features.booking.dto.response.AdminBookingSummaryResponse;
import com.trekking.app.features.booking.service.AdminBookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@SecurityRequirement(name = "bearerAuth")
public class AdminBookingController {
    private final AdminBookingService service;

    public AdminBookingController(AdminBookingService service) { this.service = service; }

    @GetMapping
    public Page<AdminBookingSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String query) {
        return service.list(status, query, page, size);
    }

    @GetMapping("/{id}")
    public AdminBookingDetailResponse get(@PathVariable Long id) { return service.get(id); }
}
