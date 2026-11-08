package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleCheckInBatchRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        OffsetDateTime startedAt,
        @Size(max = 2000) String generalNote,
        @NotEmpty @Valid List<ParticipantCheckInItemRequest> participants
) {
    public record ParticipantCheckInItemRequest(
            @NotNull Long bookingId,
            @NotNull Long participantId,
            @NotBlank String status,
            OffsetDateTime markedAt,
            @Size(max = 1000) String note,
            @Valid List<EquipmentCheckInItemRequest> equipmentItems
    ) {}

    public record EquipmentCheckInItemRequest(
            @NotNull Long equipmentId,
            String equipmentName,
            @NotNull Integer allocatedQuantity,
            @NotBlank String condition,
            @Size(max = 1000) String note,
            OffsetDateTime handedOverAt,
            @Valid List<PhotoRecordRequest> photos
    ) {}

    public record PhotoRecordRequest(
            String id,
            @NotBlank String url,
            String storageKey,
            String fileName,
            OffsetDateTime capturedAt
    ) {}
}
