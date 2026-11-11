package com.trekking.app.features.integrateai.dto.response;

import java.util.List;

public record IntegrateAiChatResponse(String sessionId, String answer, List<TourRecommendation> recommendations) {}
