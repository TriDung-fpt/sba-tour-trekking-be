package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.dto.request.AssignTourEquipmentRequest;
import com.trekking.app.features.tour.service.TourEquipmentService;
import jakarta.validation.Valid;
import com.trekking.app.features.tour.security.TourReadAccess;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tours/{tourId}")
public class TourEquipmentController {
    private final TourEquipmentService equipment;
    public TourEquipmentController(TourEquipmentService equipment) { this.equipment = equipment; }

    @GetMapping("/required-items")
    public List<RequiredItemResponse> requiredItems(@PathVariable long tourId, Authentication authentication) {
        return TourReadAccess.isPublic(authentication) ? equipment.publicRequiredItems(tourId) : equipment.requiredItems(tourId);
    }

    @GetMapping("/equipment")
    public TourEquipmentResponse equipment(@PathVariable long tourId, Authentication authentication) {
        return TourReadAccess.isPublic(authentication) ? equipment.publicEquipment(tourId) : equipment.equipment(tourId);
    }

    @PutMapping("/equipment")
    public TourEquipmentResponse assignEquipment(@PathVariable long tourId,
            @Valid @RequestBody AssignTourEquipmentRequest request) {
        return equipment.assignEquipment(tourId, request);
    }
}
