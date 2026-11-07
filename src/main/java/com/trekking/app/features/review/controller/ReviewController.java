package com.trekking.app.features.review.controller;

import com.trekking.app.features.review.dto.response.TourReviewPageResponse;
import com.trekking.app.features.review.service.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tour-visible reviews for authenticated callers.
 * Path is not under /api/v1/tours/** because TourSecurityConfig would otherwise
 * require ADMIN for this GET without editing that filter chain.
 */
@RestController
@RequestMapping("/api/v1/reviews")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public TourReviewPageResponse listByTour(@RequestParam long tourId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return reviews.listByTour(tourId, page, size);
    }
}
