package com.trekking.app.features.tour.controller;

import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.tour.dto.response.PartnerDeparturePage;
import com.trekking.app.features.tour.service.PartnerDepartureService;
import java.time.OffsetDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partner/departures")
@PreAuthorize("hasRole('COMPANY_MANAGER')")
public class PartnerDepartureController {
    private final PartnerDepartureService service;
    public PartnerDepartureController(PartnerDepartureService service) { this.service = service; }

    @GetMapping
    public PartnerDeparturePage list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user.accountId(), status, from, to, page, size);
    }
}
