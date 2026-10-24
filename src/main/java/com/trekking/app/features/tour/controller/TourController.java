package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.TourRequest;
import com.trekking.app.features.tour.dto.response.TourResponse;
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
    public TourResponse create(@Valid @RequestBody TourRequest request) {
        return tours.create(request);
    }

    @GetMapping
    public Page<TourResponse> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return tours.list(page, size);
    }

    @GetMapping("/{tourId}")
    public TourResponse read(@PathVariable long tourId) {
        return tours.read(tourId);
    }

    @PutMapping("/{tourId}")
    public TourResponse update(@PathVariable long tourId, @Valid @RequestBody TourRequest request) {
        return tours.update(tourId, request);
    }

    @DeleteMapping("/{tourId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long tourId) {
        tours.delete(tourId);
    }
}
