package com.trekking.app.features.payment.dto.request;

import tools.jackson.databind.JsonNode;

public record PayOsWebhookRequest(String code, String desc, boolean success, JsonNode data, String signature) {}
