package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.ScheduleCheckInBatchRequest;
import com.trekking.app.features.tour.dto.request.ScheduleCheckOutBatchRequest;
import com.trekking.app.features.tour.dto.response.GuideAssignedScheduleResponse;
import com.trekking.app.features.tour.dto.response.HandoverPhotoUploadResponse;
import com.trekking.app.features.tour.dto.response.ScheduleEquipmentSummaryResponse;
import com.trekking.app.features.tour.dto.response.ScheduleManifestResponse;
import com.trekking.app.features.tour.dto.response.ScheduleOperationsReportResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ScheduleOperationsService {
    List<GuideAssignedScheduleResponse> listMyAssignedSchedules();

    ScheduleManifestResponse getScheduleManifest(long scheduleId);

    ScheduleEquipmentSummaryResponse getScheduleEquipmentSummary(long scheduleId);

    HandoverPhotoUploadResponse uploadHandoverPhoto(long scheduleId, MultipartFile file);

    ScheduleOperationsReportResponse getOperationsReport(long scheduleId);

    ScheduleOperationsReportResponse.CheckInBatchReportResponse submitCheckInBatch(
            long scheduleId, ScheduleCheckInBatchRequest request);

    ScheduleOperationsReportResponse.CheckOutBatchReportResponse submitCheckOutBatch(
            long scheduleId, ScheduleCheckOutBatchRequest request);
}
