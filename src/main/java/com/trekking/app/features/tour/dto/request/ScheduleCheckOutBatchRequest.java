package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleCheckOutBatchRequest(
        @NotBlank @Size(max = 120) String idempotencyKey,
        OffsetDateTime startedAt,
        @Size(max = 2000) String generalNote,
        @NotEmpty @Valid List<ParticipantCheckOutItemRequest> participants
) {
    public record ParticipantCheckOutItemRequest(
            @NotNull Long bookingId,
            @NotNull Long participantId,
            @NotBlank String status,
            OffsetDateTime markedAt,
            @Size(max = 1000) String note,
            @Valid List<EquipmentCheckOutItemRequest> equipmentItems
    ) {}

    public record EquipmentCheckOutItemRequest(
            @NotNull Long equipmentId,
            String equipmentName,
            @NotNull Integer issuedQuantity,
            @NotNull Integer returnedQuantity,
            @NotBlank String condition,
            @Size(max = 1000) String note,
            OffsetDateTime returnedAt,
            @Valid List<ScheduleCheckInBatchRequest.PhotoRecordRequest> photos
    ) {}
}
