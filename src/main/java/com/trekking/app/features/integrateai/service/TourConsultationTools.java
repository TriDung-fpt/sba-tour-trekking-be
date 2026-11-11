package com.trekking.app.features.integrateai.service;

import com.trekking.app.features.integrateai.dto.response.TourRecommendation;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import java.util.List;
import java.util.Map;

public interface TourConsultationTools {
    List<Map<String, Object>> catalog();
    List<TourRecommendation> validateRecommendations(List<?> ids, String maxBudgetVnd,
                                                     String minBudgetVnd, String difficulty,
                                                     String departureDate, String locationQuery,
                                                     int partySize);
    List<TourSummaryResponse> publicTours();
}
