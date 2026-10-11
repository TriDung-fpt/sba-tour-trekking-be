package com.trekking.app.features.auth.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import java.time.Clock;

import com.trekking.app.features.auth.model.AuthSession;
import org.springframework.stereotype.Service;

@Service
public class SessionAccounts {
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final RedisAuthStore store;
    private final AuthCrypto crypto;
    private final Clock clock;

    public SessionAccounts(AppAccountRepository accounts, AccountRoleRepository roles, RedisAuthStore store,
                           AuthCrypto crypto, Clock clock) {
        this.accounts = accounts;
        this.roles = roles;
        this.store = store;
        this.crypto = crypto;
        this.clock = clock;
    }

    public AppAccount validate(AuthSession session) {
        AppAccount account = accounts.findById(session.accountId()).orElseThrow(AuthException::invalidSession);
        if (session.expiresAt().isBefore(clock.instant()) || account.getStatus() != AccountStatus.ACTIVE
                || account.getEmailVerifiedAt() == null || Boolean.TRUE.equals(account.getMustChangePassword())
                || !crypto.same(session.passwordFingerprint(), crypto.passwordFingerprint(account.getPasswordHash()))) {
            store.revoke(session.id());
            throw AuthException.invalidSession();
        }
        return account;
    }

    public AccountResponse response(AppAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getFullName(), account.getPhone(),
                roles.findAllByAccountId(account.getId()).stream().map(r -> r.getRole().getValue()).sorted().toList());
    }
}
