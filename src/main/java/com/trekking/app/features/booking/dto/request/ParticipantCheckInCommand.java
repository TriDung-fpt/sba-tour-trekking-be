package com.trekking.app.features.booking.dto.request;

public record ParticipantCheckInCommand(long scheduleId, long bookingId, long participantId, long actorId,
                                        String note) {}
