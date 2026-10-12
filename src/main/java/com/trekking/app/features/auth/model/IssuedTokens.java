package com.trekking.app.features.auth.model;

import com.trekking.app.features.auth.dto.response.AuthResponse;
import java.time.Instant;

public record IssuedTokens(AuthResponse response, String refreshToken, Instant sessionExpiresAt) {}
