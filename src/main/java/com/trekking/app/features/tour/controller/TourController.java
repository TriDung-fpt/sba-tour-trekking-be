package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import com.trekking.app.features.tour.service.TourService;
import com.trekking.app.features.tour.security.TourReadAccess;
import com.trekking.app.features.tour.enums.DifficultyLevel;
import com.trekking.app.features.tour.enums.TourStatus;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours")
public class TourController {
    private final TourService tours;

    public TourController(TourService tours) {
        this.tours = tours;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    public TourDetailResponse create(@Valid @RequestBody CreateTourRequest request) {
        return tours.create(request);
    }

    @GetMapping
    public Page<TourSummaryResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String query,
                                          @RequestParam(required = false) DifficultyLevel difficulty,
                                          @RequestParam(required = false) Integer durationDays,
                                          @RequestParam(required = false) TourStatus status,
                                          Authentication authentication) {
        if (TourReadAccess.isPublic(authentication)) {
            return tours.listPublic(query, difficulty, durationDays, page, size);
        }
        if (query == null && difficulty == null && durationDays == null && status == null) {
            return tours.list(page, size);
        }
        return tours.list(query, difficulty, durationDays, status, page, size);
    }

    @GetMapping("/{tourId}")
    public TourDetailResponse read(@PathVariable long tourId, Authentication authentication) {
        return TourReadAccess.isPublic(authentication) ? tours.readPublic(tourId) : tours.read(tourId);
    }

    @PutMapping("/{tourId}")
    @SecurityRequirement(name = "bearerAuth")
    public TourDetailResponse update(@PathVariable long tourId, @Valid @RequestBody UpdateTourRequest request) {
        return tours.update(tourId, request);
    }

    @DeleteMapping("/{tourId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void delete(@PathVariable long tourId) {
        tours.delete(tourId);
    }
}
