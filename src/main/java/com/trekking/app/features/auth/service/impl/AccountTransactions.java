package com.trekking.app.features.auth.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.account.entity.AccountRole;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.RoleType;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import java.time.Clock;
import java.time.OffsetDateTime;

import com.trekking.app.features.otp.enums.OtpPurpose;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTransactions {
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final PasswordEncoder passwords;
    private final RedisAuthStore store;
    private final AuthCrypto crypto;
    private final Clock clock;

    public AccountTransactions(AppAccountRepository accounts, AccountRoleRepository roles, PasswordEncoder passwords,
                               RedisAuthStore store, AuthCrypto crypto, Clock clock) {
        this.accounts = accounts;
        this.roles = roles;
        this.passwords = passwords;
        this.store = store;
        this.crypto = crypto;
        this.clock = clock;
    }

    @Transactional
    public AppAccount register(AuthRequests.Register request) {
        if (accounts.findByEmail(request.email()).isPresent()) throw duplicateEmail();
        var account = new AppAccount();
        account.setEmail(request.email());
        account.setFullName(request.fullName());
        account.setPhone(request.phone());
        account.setPasswordHash(passwords.encode(request.password()));
        accounts.saveAndFlush(account);
        var role = new AccountRole();
        role.setAccountId(account.getId());
        role.setRole(RoleType.CUSTOMER);
        roles.saveAndFlush(role);
        return account;
    }

    public static AuthException duplicateEmail() {
        return new AuthException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email is already registered");
    }

    public String context(AppAccount account) {
        return account.getId() + ":" + crypto.passwordFingerprint(account.getPasswordHash());
    }

    public String otpHash(String email, OtpPurpose purpose, String code) {
        return crypto.hmac("otp:" + purpose + ":" + email + ":" + code);
    }

    @Transactional
    public void verify(String email, String otp) {
        AppAccount account = eligible(email);
        store.consumeOtp(email, OtpPurpose.VERIFY_EMAIL, otpHash(email, OtpPurpose.VERIFY_EMAIL, otp), context(account));
        if (account.getEmailVerifiedAt() == null) account.setEmailVerifiedAt(OffsetDateTime.now(clock));
    }

    @Transactional
    public void reset(String email, String otp, String newPassword) {
        AppAccount account = eligible(email);
        store.consumeOtp(email, OtpPurpose.RESET_PASSWORD, otpHash(email, OtpPurpose.RESET_PASSWORD, otp), context(account));
        account.setPasswordHash(passwords.encode(newPassword));
        account.setMustChangePassword(false);
        accounts.flush();
    }

    private AppAccount eligible(String email) {
        return accounts.lockByEmail(email).filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "OTP is invalid or expired"));
    }
}
