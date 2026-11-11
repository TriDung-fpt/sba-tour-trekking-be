package com.trekking.app.features.integrateai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.integrate")
public record IntegrateAiProperties(boolean enabled, GeminiProperties gemini, SessionProperties session,
                                    AiRequestLimitsProperties limits, TourCatalogProperties catalog,
                                    BudgetProperties budget) {}
