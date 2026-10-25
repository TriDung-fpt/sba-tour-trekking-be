package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.CreateActivityRequest;
import com.trekking.app.features.tour.dto.request.UpdateActivityRequest;
import com.trekking.app.features.tour.dto.response.ActivityResponse;
import com.trekking.app.features.tour.service.TourActivityService;
import com.trekking.app.features.tour.security.TourReadAccess;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tours/{tourId}/activities")
public class TourActivityController {
    private final TourActivityService activities;

    public TourActivityController(TourActivityService activities) {
        this.activities = activities;
    }

    @GetMapping
    public List<ActivityResponse> list(@PathVariable long tourId, Authentication authentication) {
        return TourReadAccess.isPublic(authentication) ? activities.listPublic(tourId) : activities.list(tourId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public ActivityResponse create(@PathVariable long tourId, @Valid @RequestBody CreateActivityRequest request) {
        return activities.create(tourId, request);
    }

    @PutMapping("/{activityId}")
    @SecurityRequirement(name = "bearerAuth")
    public ActivityResponse update(@PathVariable long tourId, @PathVariable long activityId,
                                   @Valid @RequestBody UpdateActivityRequest request) {
        return activities.update(tourId, activityId, request);
    }

    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable long tourId, @PathVariable long activityId) {
        activities.delete(tourId, activityId);
    }
}
