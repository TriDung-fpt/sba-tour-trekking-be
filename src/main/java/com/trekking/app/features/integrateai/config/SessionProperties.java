package com.trekking.app.features.integrateai.config;

import java.time.Duration;

public record SessionProperties(Duration ttl, int maxTurns) {}
