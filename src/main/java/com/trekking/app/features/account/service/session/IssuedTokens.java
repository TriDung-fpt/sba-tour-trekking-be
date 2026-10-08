package com.trekking.app.features.account.service.session;

import com.trekking.app.features.account.dto.response.AuthResponse;
import java.time.Instant;

public record IssuedTokens(AuthResponse response, String refreshToken, Instant sessionExpiresAt) {}
