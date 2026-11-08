package com.trekking.app.features.tour.dto.response;

import com.trekking.app.features.equipment.dto.response.EquipmentReservationResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleManifestResponse(
        long scheduleId,
        long tourId,
        String tourTitle,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String meetingPoint,
        String contactPhone,
        int capacity,
        int confirmedSeats,
        int heldSeats,
        List<ScheduleGuideSummaryResponse> assignedGuides,
        List<ManifestBookingItem> bookings
) {
    public record ManifestBookingItem(
            long bookingId,
            String bookingCode,
            String customerName,
            String customerPhone,
            String customerEmail,
            String status,
            List<ManifestParticipantItem> passengers,
            EquipmentReservationResponse equipmentReservation
    ) {}

    public record ManifestParticipantItem(
            long participantId,
            long bookingId,
            String bookingCode,
            String fullName,
            String phone,
            String email,
            boolean bookingCustomer,
            boolean checkedIn,
            OffsetDateTime checkedInAt,
            Long checkedInBy,
            boolean checkedOut,
            OffsetDateTime checkedOutAt,
            Long checkedOutBy,
            String note
    ) {}
}
