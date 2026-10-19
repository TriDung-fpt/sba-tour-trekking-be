package com.trekking.app.features.auth.dto.response;

import com.trekking.app.features.account.dto.response.AccountResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
