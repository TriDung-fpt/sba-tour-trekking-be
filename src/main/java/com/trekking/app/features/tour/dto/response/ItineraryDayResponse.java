package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record ItineraryDayResponse(int dayNumber, String title, String meals, String overnight,
                                   List<ActivityResponse> activities) {}
