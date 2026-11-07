package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.response.TourStatisticsResponse;
import com.trekking.app.features.tour.service.TourStatisticsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class TourStatisticsController {
    private final TourStatisticsService statistics;

    public TourStatisticsController(TourStatisticsService statistics) { this.statistics = statistics; }

    @GetMapping("/api/v1/tours/{tourId}/statistics")
    public TourStatisticsResponse read(@PathVariable long tourId) { return statistics.read(tourId); }
}
