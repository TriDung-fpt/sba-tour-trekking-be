package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record RosterResponse(long scheduleId, List<Participant> participants) {}
