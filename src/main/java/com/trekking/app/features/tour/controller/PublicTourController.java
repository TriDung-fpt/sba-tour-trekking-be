package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import com.trekking.app.features.tour.service.TourService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PublicTourController {
    private final TourService tours;

    public PublicTourController(TourService tours) {
        this.tours = tours;
    }

    @GetMapping("/tours")
    public TourPageResponse search(@Valid @ModelAttribute TourSearchRequest request) {
        return tours.searchPublic(request);
    }

    @GetMapping("/tours/{tourId}")
    public TourDetailResponse detail(@PathVariable long tourId) {
        return tours.getPublicDetail(tourId);
    }

    @GetMapping("/tours/{tourId}/reviews")
    public ReviewPage reviews(@PathVariable long tourId,
                              @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
                              @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return tours.publicReviews(tourId, page, size);
    }

    @GetMapping("/locations")
    public List<LocationResponse> locations() {
        return tours.listLocations();
    }
}
