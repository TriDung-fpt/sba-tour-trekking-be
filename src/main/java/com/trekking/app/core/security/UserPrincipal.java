package com.trekking.app.core.security;

import com.trekking.app.features.account.dto.response.AccountResponse;

public record UserPrincipal(long accountId, String sessionId, AccountResponse account) {}
