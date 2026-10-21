package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record Participant(String bookingCode, long participantId, String fullName, String phone,
                          OffsetDateTime checkedInAt, String checkInNote) {}
