package com.trekking.app.features.booking.dto.response;

import java.time.OffsetDateTime;

public record ParticipantCheckInResponse(long participantId, long bookingId, OffsetDateTime checkedInAt,
                                         long checkedInBy, String note) {}
