package com.trekking.app.features.review.controller;

import com.trekking.app.features.review.dto.request.ReviewModerationRequest;
import com.trekking.app.features.review.dto.response.AdminReviewDetailResponse;
import com.trekking.app.features.review.dto.response.AdminReviewPageResponse;
import com.trekking.app.features.review.service.ReviewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@SecurityRequirement(name = "bearerAuth")
public class AdminReviewController {
    private final ReviewService reviews;

    public AdminReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public AdminReviewPageResponse list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) Integer rating,
                                        @RequestParam(required = false) Long tourId,
                                        @RequestParam(required = false) String createdFrom,
                                        @RequestParam(required = false) String createdTo,
                                        @RequestParam(required = false) String sort) {
        return reviews.listAdmin(page, size, q, status, rating, tourId, createdFrom, createdTo, sort);
    }

    @GetMapping("/{id}")
    public AdminReviewDetailResponse get(@PathVariable long id) {
        return reviews.getAdmin(id);
    }

    @PostMapping("/{id}/hide")
    public AdminReviewDetailResponse hide(@PathVariable long id,
                                          @Valid @RequestBody ReviewModerationRequest request) {
        return reviews.hide(id, request.reason());
    }

    @PostMapping("/{id}/restore")
    public AdminReviewDetailResponse restore(@PathVariable long id,
                                             @Valid @RequestBody ReviewModerationRequest request) {
        return reviews.restore(id, request.reason());
    }
}
