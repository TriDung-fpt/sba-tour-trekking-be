package com.trekking.app.features.equipment.controller;

import com.trekking.app.features.equipment.dto.request.EquipmentHandoverRequest;
import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;
import com.trekking.app.features.equipment.service.EquipmentHandoverService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/equipment")
@SecurityRequirement(name = "bearerAuth")
public class EquipmentHandoverController {
    private final EquipmentHandoverService handovers;

    public EquipmentHandoverController(EquipmentHandoverService handovers) { this.handovers = handovers; }

    @GetMapping("/reservation")
    public EquipmentReservationResponse read(@PathVariable long bookingId) { return handovers.read(bookingId); }

    @PostMapping("/issue")
    public EquipmentReservationResponse issue(@PathVariable long bookingId,
            @Valid @RequestBody EquipmentHandoverRequest request) {
        return handovers.issue(bookingId, request);
    }

    @PostMapping("/return")
    public EquipmentReservationResponse recordReturn(@PathVariable long bookingId,
            @Valid @RequestBody EquipmentHandoverRequest request) {
        return handovers.recordReturn(bookingId, request);
    }
}
