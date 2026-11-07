package com.trekking.app.features.review.controller;

import com.trekking.app.features.review.dto.request.CreateReviewRequest;
import com.trekking.app.features.review.dto.response.CustomerReviewResponse;
import com.trekking.app.features.review.service.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingReviewController {
    private final ReviewService reviews;

    public BookingReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @PostMapping("/{bookingId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerReviewResponse create(@PathVariable long bookingId,
                                         @Valid @RequestBody CreateReviewRequest request) {
        return reviews.create(bookingId, request);
    }
}
