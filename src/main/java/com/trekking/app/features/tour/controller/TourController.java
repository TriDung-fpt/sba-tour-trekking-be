package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import com.trekking.app.features.tour.service.TourService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours")
@SecurityRequirement(name = "bearerAuth")
public class TourController {
    private final TourService tours;

    public TourController(TourService tours) {
        this.tours = tours;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TourDetailResponse create(@Valid @RequestBody CreateTourRequest request) {
        return tours.create(request);
    }

    @GetMapping
    public Page<TourSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return tours.list(page, size);
    }

    @GetMapping("/{tourId}")
    public TourDetailResponse read(@PathVariable long tourId) {
        return tours.read(tourId);
    }

    @PutMapping("/{tourId}")
    public TourDetailResponse update(@PathVariable long tourId, @Valid @RequestBody UpdateTourRequest request) {
        return tours.update(tourId, request);
    }

    @DeleteMapping("/{tourId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long tourId) {
        tours.delete(tourId);
    }
}
