package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record ParticipantCheckOutResponse(long participantId, long bookingId, OffsetDateTime checkedOutAt,
                                          long checkedOutBy, String note) {}
