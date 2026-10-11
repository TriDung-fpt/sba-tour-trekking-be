package com.trekking.app.features.auth.model;

import java.time.Instant;

public record AuthSession(String id, long accountId, String passwordFingerprint, Instant expiresAt) {}
