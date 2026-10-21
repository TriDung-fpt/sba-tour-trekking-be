package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record Participant(String bookingCode, String fullName, String phone, String emergencyContact) {}
