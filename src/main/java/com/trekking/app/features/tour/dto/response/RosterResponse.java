package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record RosterResponse(long scheduleId, List<Participant> participants,
                             int page, int size, long totalItems, int totalPages) {}
