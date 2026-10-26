package com.trekking.app.features.support.controller;

import com.trekking.app.features.support.dto.request.ReviewModerationRequests;
import com.trekking.app.features.support.dto.response.ReviewResponse;
import com.trekking.app.features.support.service.ReviewModerationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
    private final ReviewModerationService service;

    public AdminReviewController(ReviewModerationService service) { this.service = service; }

    @GetMapping
    public Page<ReviewResponse> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Long bookingId) {
        return service.list(status, bookingId, page, size);
    }

    @PostMapping("/{id}/hide")
    public ReviewResponse hide(@PathVariable Long id,
                               @Valid @RequestBody ReviewModerationRequests.Reason request) {
        return service.hide(id, request.reason());
    }

    @PostMapping("/{id}/restore")
    public ReviewResponse restore(@PathVariable Long id,
                                  @Valid @RequestBody ReviewModerationRequests.Reason request) {
        return service.restore(id, request.reason());
    }
}
