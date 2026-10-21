package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.request.*;
import com.trekking.app.features.tour.dto.response.*;






import java.util.List;
import org.springframework.data.domain.Page;

public interface TourWorkflowService {
    Page<TourItem> partnerTours(long accountId, int page, int size, String status);
    List<CancellationPolicyVersion> cancellationPolicyVersions(long accountId);
    TourDetail createTour(long accountId, TourWriteRequest request);
    TourDetail getPartnerTour(long accountId, long tourId);
    TourDetail patchTour(long accountId, long tourId, TourPatchRequest request);
    ActionMessage archiveTour(long accountId, long tourId);
    ActionMessage restoreTour(long accountId, long tourId);
    ActionMessage submitTour(long accountId, long tourId);
    Page<ReviewQueueItem> reviewQueue(int page, int size);
    ActionMessage approve(long adminId, long tourId);
    ActionMessage reject(long adminId, long tourId, String reason);
    TourManagementEquipment putEquipmentPackage(long accountId, long tourId, EquipmentPackage request);
    TourManagementSchedule createSchedule(long accountId, long tourId, ScheduleCreate request);
    TourManagementSchedule patchSchedule(long accountId, long tourId, long scheduleId, SchedulePatch request);
    ActionMessage cancelSchedule(long accountId, long tourId, long scheduleId, CancelSchedule request);
    ActionMessage completeSchedule(long accountId, long tourId, long scheduleId);
    RosterResponse roster(long accountId, long tourId, long scheduleId, int page, int size);
    byte[] rosterXlsx(long accountId, long tourId, long scheduleId);
}
