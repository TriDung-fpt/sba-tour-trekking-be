package com.trekking.app.features.report.controller;

import com.trekking.app.features.report.dto.response.DashboardResponse;
import com.trekking.app.features.report.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public DashboardResponse dashboard(@RequestParam String start, @RequestParam String end) {
        return service.dashboard(start, end);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam String start, @RequestParam String end) {
        var filename = "dashboard_" + start + "_" + end + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(service.exportCsv(start, end));
    }
}
