package com.trekking.app.features.account.service.session;

import java.time.Instant;

public record AuthSession(String id, long accountId, String passwordFingerprint, Instant expiresAt) {}
