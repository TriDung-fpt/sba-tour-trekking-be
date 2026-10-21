package com.trekking.app.features.booking.dto.response;

import com.trekking.app.features.booking.dto.request.BookingParticipantRequest;
import java.time.OffsetDateTime;
import java.util.List;

public record CreatedBookingResponse(Long id, String bookingCode, String status, OffsetDateTime holdExpiresAt,
                                     int participantCount, long totalPriceVnd, long depositDueVnd,
                                     OffsetDateTime balanceDueAt, List<Participant> participants) {
    public record Participant(Long id, String fullName, String email, String phone) {}
}
