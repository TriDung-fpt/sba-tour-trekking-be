package com.trekking.app.features.tour.dto.response;

import java.time.LocalTime;

public record TourManagementActivity(int dayNumber, int displayOrder, String title, String description, LocalTime startTime, Long locationId) {}
