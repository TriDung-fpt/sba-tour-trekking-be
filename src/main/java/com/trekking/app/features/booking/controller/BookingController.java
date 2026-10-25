package com.trekking.app.features.booking.controller;

import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDetailResponse create(@Valid @RequestBody CreateBookingRequest request) {
        return bookings.create(request);
    }

    @GetMapping("/my")
    public Page<BookingSummaryResponse> myBookings(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookings.myBookings(page, size);
    }

    @GetMapping("/{bookingId}")
    public BookingDetailResponse get(@PathVariable @Min(1) long bookingId) {
        return bookings.get(bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingDetailResponse cancel(@PathVariable @Min(1) long bookingId,
            @Valid @RequestBody CancelBookingRequest request) {
        return bookings.cancel(bookingId, request);
    }
}
