package com.trekking.app.features.integrateai.config;

import java.time.Duration;

public record GeminiProperties(String apiKey, String baseUrl, String model, double temperature,
                               int maxOutputTokens, Duration timeout) {}
