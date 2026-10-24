package com.trekking.app.core.security;

import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.auth.model.AuthSession;
public interface JwtService {
    String issue(AuthSession session, AccountResponse account);
}
