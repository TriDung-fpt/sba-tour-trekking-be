package com.trekking.app.features.booking.controller;

import com.trekking.app.features.booking.dto.request.BookingSearchCriteria;
import com.trekking.app.features.booking.dto.request.CancelBookingRequest;
import com.trekking.app.features.booking.dto.request.CreateBookingRequest;
import com.trekking.app.features.booking.dto.request.ReplacePassengersRequest;
import com.trekking.app.features.booking.dto.request.UpdatePassengerRequest;
import com.trekking.app.features.booking.dto.response.BookingCancellationResponse;
import com.trekking.app.features.booking.dto.response.BookingCreateResult;
import com.trekking.app.features.booking.dto.response.BookingDetailResponse;
import com.trekking.app.features.booking.dto.response.BookingSummaryResponse;
import com.trekking.app.features.booking.enums.BookingStatus;
import com.trekking.app.features.booking.service.BookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    public ResponseEntity<BookingDetailResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        BookingCreateResult result = bookings.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.booking());
    }

    @GetMapping("/my")
    public Page<BookingSummaryResponse> mine(@RequestParam(required = false) BookingStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return bookings.listMine(status, page, size);
    }

    @GetMapping
    public Page<BookingSummaryResponse> search(@ModelAttribute BookingSearchCriteria criteria,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return bookings.search(criteria, page, size);
    }

    @GetMapping("/{bookingId}")
    public BookingDetailResponse read(@PathVariable long bookingId) {
        return bookings.get(bookingId);
    }

    @PutMapping("/{bookingId}/passengers")
    public BookingDetailResponse replacePassengers(@PathVariable long bookingId,
                                                   @Valid @RequestBody ReplacePassengersRequest request) {
        return bookings.replacePassengers(bookingId, request);
    }

    @PatchMapping("/{bookingId}/passengers/{participantId}")
    public BookingDetailResponse updatePassenger(@PathVariable long bookingId, @PathVariable long participantId,
                                                 @Valid @RequestBody UpdatePassengerRequest request) {
        return bookings.updatePassenger(bookingId, participantId, request);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingCancellationResponse cancel(@PathVariable long bookingId,
                                              @Valid @RequestBody CancelBookingRequest request) {
        return bookings.cancel(bookingId, request);
    }
}
