package com.trekking.app.features.integrateai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IntegrateAiChatRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 36) String sessionId) {}
