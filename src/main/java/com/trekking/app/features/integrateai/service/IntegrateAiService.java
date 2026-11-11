package com.trekking.app.features.integrateai.service;

import com.trekking.app.features.integrateai.dto.response.IntegrateAiChatResponse;

public interface IntegrateAiService {
    IntegrateAiChatResponse chat(String question, String sessionId, String clientKey);
}
