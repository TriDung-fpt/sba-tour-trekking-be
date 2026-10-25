package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.service.TourEquipmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours/{tourId}")
@SecurityRequirement(name = "bearerAuth")
public class TourEquipmentController {
    private final TourEquipmentService equipment;
    public TourEquipmentController(TourEquipmentService equipment) { this.equipment = equipment; }

    @GetMapping("/required-items")
    public List<RequiredItemResponse> requiredItems(@PathVariable long tourId) { return equipment.requiredItems(tourId); }

    @GetMapping("/equipment")
    public TourEquipmentResponse equipment(@PathVariable long tourId) { return equipment.equipment(tourId); }
}
