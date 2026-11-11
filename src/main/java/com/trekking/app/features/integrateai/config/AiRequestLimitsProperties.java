package com.trekking.app.features.integrateai.config;

import java.time.Duration;

public record AiRequestLimitsProperties(int maxMessageChars, int requestsPerWindow, Duration rateWindow) {}
