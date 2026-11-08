package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.ScheduleCheckInBatchRequest;
import com.trekking.app.features.tour.dto.request.ScheduleCheckOutBatchRequest;
import com.trekking.app.features.tour.dto.response.GuideAssignedScheduleResponse;
import com.trekking.app.features.tour.dto.response.HandoverPhotoUploadResponse;
import com.trekking.app.features.tour.dto.response.ScheduleEquipmentSummaryResponse;
import com.trekking.app.features.tour.dto.response.ScheduleManifestResponse;
import com.trekking.app.features.tour.dto.response.ScheduleOperationsReportResponse;
import com.trekking.app.features.tour.service.ScheduleOperationsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleOperationsController {
    private final ScheduleOperationsService operations;

    public ScheduleOperationsController(ScheduleOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping({"/guides/me/schedules", "/schedules/assigned-to-me"})
    public List<GuideAssignedScheduleResponse> listMyAssignedSchedules() {
        return operations.listMyAssignedSchedules();
    }

    @GetMapping("/schedules/{scheduleId}/manifest")
    public ScheduleManifestResponse getScheduleManifest(@PathVariable long scheduleId) {
        return operations.getScheduleManifest(scheduleId);
    }

    @GetMapping("/schedules/{scheduleId}/equipment-summary")
    public ScheduleEquipmentSummaryResponse getScheduleEquipmentSummary(@PathVariable long scheduleId) {
        return operations.getScheduleEquipmentSummary(scheduleId);
    }

    @PostMapping(
            value = "/schedules/{scheduleId}/handover-photos",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public HandoverPhotoUploadResponse uploadHandoverPhoto(
            @PathVariable long scheduleId,
            @RequestPart("file") MultipartFile file) {
        return operations.uploadHandoverPhoto(scheduleId, file);
    }

    @GetMapping("/schedules/{scheduleId}/operations-report")
    public ScheduleOperationsReportResponse getOperationsReport(@PathVariable long scheduleId) {
        return operations.getOperationsReport(scheduleId);
    }

    @PostMapping("/schedules/{scheduleId}/check-in-batch")
    public ScheduleOperationsReportResponse.CheckInBatchReportResponse submitCheckInBatch(
            @PathVariable long scheduleId,
            @Valid @RequestBody ScheduleCheckInBatchRequest request) {
        return operations.submitCheckInBatch(scheduleId, request);
    }

    @PostMapping("/schedules/{scheduleId}/check-out-batch")
    public ScheduleOperationsReportResponse.CheckOutBatchReportResponse submitCheckOutBatch(
            @PathVariable long scheduleId,
            @Valid @RequestBody ScheduleCheckOutBatchRequest request) {
        return operations.submitCheckOutBatch(scheduleId, request);
    }
}
