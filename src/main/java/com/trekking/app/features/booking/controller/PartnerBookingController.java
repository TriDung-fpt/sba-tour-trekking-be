package com.trekking.app.features.booking.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.booking.dto.response.PartnerBookingDetail;
import com.trekking.app.features.booking.dto.response.PartnerBookingPage;
import com.trekking.app.features.booking.dto.request.CheckInRequest;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.response.BookingActionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.trekking.app.features.booking.service.PartnerBookingService;
import java.time.OffsetDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/partner/bookings")
public class PartnerBookingController {
    private final PartnerBookingService service;

    public PartnerBookingController(PartnerBookingService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public PartnerBookingPage list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime dateFrom,
            @RequestParam(required = false) OffsetDateTime dateTo,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(user.accountId(), status, dateFrom, dateTo, query, page, size);
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public PartnerBookingDetail detail(@AuthenticationPrincipal UserPrincipal user, @PathVariable long bookingId) {
        return service.detail(user.accountId(), bookingId);
    }

    @PostMapping("/{bookingId}/participants/{participantId}/check-in")
    @PreAuthorize("hasAnyRole('COMPANY_MANAGER','GUIDE')")
    public BookingActionResponse checkIn(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookingId, @PathVariable long participantId,
            @Valid @RequestBody(required = false) CheckInRequest request) {
        return service.checkIn(user.accountId(), bookingId, participantId, request);
    }

    @DeleteMapping("/{bookingId}/participants/{participantId}/check-in")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public BookingActionResponse undoCheckIn(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookingId, @PathVariable long participantId,
            @RequestBody UndoCheckInRequest request) {
        return service.undoCheckIn(user.accountId(), bookingId, participantId, request.reason());
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('COMPANY_MANAGER')")
    public BookingActionResponse cancel(@AuthenticationPrincipal UserPrincipal user,
            @PathVariable long bookingId, @Valid @RequestBody CancelBookingRequest request) {
        return service.cancel(user.accountId(), bookingId, request);
    }

    public record UndoCheckInRequest(@NotBlank @Size(max = 1000) String reason) {}
}
