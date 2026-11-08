package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleOperationsReportResponse(
        long scheduleId,
        Long tourId,
        String tourTitle,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        CheckInBatchReportResponse checkInReport,
        CheckOutBatchReportResponse checkOutReport,
        OffsetDateTime updatedAt
) {
    public record PhotoItemResponse(
            String id,
            String url,
            String storageKey,
            String fileName,
            OffsetDateTime capturedAt
    ) {}

    public record EquipmentCheckInItemResponse(
            long scheduleId,
            long bookingId,
            long participantId,
            long equipmentId,
            String equipmentName,
            int allocatedQuantity,
            String condition,
            String note,
            List<PhotoItemResponse> photos,
            OffsetDateTime handedOverAt
    ) {}

    public record ParticipantCheckInItemResponse(
            long scheduleId,
            long bookingId,
            String bookingCode,
            long participantId,
            String fullName,
            String phone,
            String email,
            boolean bookingCustomer,
            String status,
            OffsetDateTime markedAt,
            String note,
            List<EquipmentCheckInItemResponse> equipmentItems
    ) {}

    public record CheckInBatchReportResponse(
            long scheduleId,
            String roundStatus,
            String idempotencyKey,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            Long submittedByGuideId,
            String submittedByGuideName,
            String submittedByGuideEmail,
            String generalNote,
            String lastErrorMessage,
            List<ParticipantCheckInItemResponse> participants
    ) {}

    public record EquipmentCheckOutItemResponse(
            long scheduleId,
            long bookingId,
            long participantId,
            long equipmentId,
            String equipmentName,
            int issuedQuantity,
            int returnedQuantity,
            String condition,
            String note,
            List<PhotoItemResponse> photos,
            OffsetDateTime returnedAt
    ) {}

    public record ParticipantCheckOutItemResponse(
            long scheduleId,
            long bookingId,
            String bookingCode,
            long participantId,
            String fullName,
            String phone,
            String email,
            boolean bookingCustomer,
            String checkInStatus,
            String status,
            OffsetDateTime markedAt,
            String note,
            List<EquipmentCheckOutItemResponse> equipmentItems
    ) {}

    public record CheckOutBatchReportResponse(
            long scheduleId,
            String roundStatus,
            String idempotencyKey,
            OffsetDateTime startedAt,
            OffsetDateTime submittedAt,
            Long submittedByGuideId,
            String submittedByGuideName,
            String submittedByGuideEmail,
            String generalNote,
            String lastErrorMessage,
            List<ParticipantCheckOutItemResponse> participants
    ) {}
}
