package com.trekking.app.features.auth.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.auth.model.GoogleIdentity;
import com.trekking.app.features.auth.validation.AuthValidation;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccountTransactions {
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final PasswordEncoder passwords;
    private final AuthCrypto crypto;
    private final AuthValidation validation;
    private final Clock clock;

    public GoogleAccountTransactions(AppAccountRepository accounts, AccountRoleRepository roles,
                                     PasswordEncoder passwords, AuthCrypto crypto,
                                     AuthValidation validation, Clock clock) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwords = passwords;
        this.crypto = crypto;
        this.validation = validation;
        this.clock = clock;
    }

    @Transactional
    public AppAccount resolve(GoogleIdentity identity) {
        // A returning Google user is identified by sub even if their Google email changes.
        var linked = accounts.lockByGoogleSubject(identity.subject());
        if (linked.isPresent()) {
            validation.validateLoginAccount(linked.get());
            return linked.get();
        }
        if (!identity.emailAuthoritative()) {
            throw new AuthException(HttpStatus.FORBIDDEN, "GOOGLE_EMAIL_VERIFICATION_REQUIRED",
                    "Use a Gmail or Google Workspace account; other emails require separate ownership verification");
        }
        var existing = accounts.lockByEmail(identity.email());
        if (existing.isPresent()) {
            var account = existing.get();
            validation.validateLoginAccount(account);
            if (account.getGoogleSubject() != null && !identity.subject().equals(account.getGoogleSubject())) {
                throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_CONFLICT",
                        "This email is linked to a different Google account");
            }
            account.setGoogleSubject(identity.subject());
            accounts.flush();
            return account;
        }
        var account = new AppAccount();
        account.setEmail(identity.email());
        account.setGoogleSubject(identity.subject());
        account.setFullName(identity.fullName());
        // Keep the existing non-null hash and password-reset/session-revocation contract.
        // This random credential is never returned; users can set a password via the OTP reset flow.
        account.setPasswordHash(passwords.encode(crypto.token()));
        account.setEmailVerifiedAt(OffsetDateTime.now(clock));
        accounts.saveAndFlush(account);
        var role = new AccountRole();
        role.setAccountId(account.getId());
        role.setRole(RoleType.CUSTOMER);
        roles.saveAndFlush(role);
        return account;
    }
}
