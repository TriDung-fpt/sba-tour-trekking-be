package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.SaveTourLocationsRequest;
import com.trekking.app.features.tour.dto.request.CreateLocationRequest;
import com.trekking.app.features.tour.dto.response.LocationResponse;
import com.trekking.app.features.tour.service.TourLocationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class TourLocationController {
    private final TourLocationService locations;

    public TourLocationController(TourLocationService locations) {
        this.locations = locations;
    }

    @GetMapping("/admin/tour-locations")
    public List<LocationResponse> listAvailable() {
        return locations.listAvailable();
    }

    @PostMapping("/admin/tour-locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return locations.createLocation(request);
    }

    @GetMapping("/tours/{tourId}/locations")
    public List<LocationResponse> listForTour(@PathVariable long tourId) {
        return locations.listForTour(tourId);
    }

    @PutMapping("/tours/{tourId}/locations")
    public List<LocationResponse> save(@PathVariable long tourId,
                                       @Valid @RequestBody SaveTourLocationsRequest request) {
        return locations.save(tourId, request);
    }
}
