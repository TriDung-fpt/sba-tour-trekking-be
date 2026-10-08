package com.trekking.app.features.account.dto.response;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, AccountResponse account) {}
