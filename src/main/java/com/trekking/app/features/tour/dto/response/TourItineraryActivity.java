package com.trekking.app.features.tour.dto.response;

import java.time.LocalTime;

public record TourItineraryActivity(int dayNumber, String title, String description, LocalTime startTime, Long locationId) {}
