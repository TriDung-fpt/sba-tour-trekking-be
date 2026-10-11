package com.trekking.app.features.auth.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.core.security.JwtService;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.auth.dto.response.AuthResponse;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.trekking.app.features.auth.service.AuthService;
import com.trekking.app.features.auth.service.OtpMailSender;
import com.trekking.app.features.auth.enums.OtpPurpose;
import com.trekking.app.features.auth.service.AuthSession;
import com.trekking.app.features.auth.service.IssuedTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final AppAccountRepository accounts;
    private final AccountTransactions transactions;
    private final RedisAuthStore store;
    private final AuthCrypto crypto;
    private final PasswordEncoder passwords;
    private final OtpMailSender mail;
    private final JwtService jwt;
    private final SessionAccounts sessionAccounts;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public AuthServiceImpl(AppAccountRepository accounts, AccountTransactions transactions, RedisAuthStore store,
                           AuthCrypto crypto, PasswordEncoder passwords, OtpMailSender mail, JwtService jwt,
                           SessionAccounts sessionAccounts, AuthProperties properties, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.store = store;
        this.crypto = crypto;
        this.passwords = passwords;
        this.mail = mail;
        this.jwt = jwt;
        this.sessionAccounts = sessionAccounts;
        this.properties = properties;
        this.clock = clock;
        dummyHash = passwords.encode(crypto.token());
    }

    @Override
    public void register(AuthRequests.Register request) {
        AppAccount account;
        try { account = transactions.register(request); }
        catch (DataIntegrityViolationException ex) {
            if (accounts.findByEmail(request.email()).isPresent()) throw AccountTransactions.duplicateEmail();
            throw ex;
        }
        if (store.reserveEmail(request.email(), OtpPurpose.VERIFY_EMAIL)) sendOtp(account, OtpPurpose.VERIFY_EMAIL, true);
    }

    @Override
    public void verifyEmail(AuthRequests.VerifyEmail request) { transactions.verify(request.email(), request.otp()); }

    @Override
    public void requestOtp(String email, OtpPurpose purpose) {
        if (!store.reserveEmail(email, purpose)) return;
        accounts.findByEmail(email).filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> purpose != OtpPurpose.VERIFY_EMAIL || a.getEmailVerifiedAt() == null)
                .ifPresent(a -> sendOtp(a, purpose, false));
    }

    private void sendOtp(AppAccount account, OtpPurpose purpose, boolean reportFailure) {
        String email = account.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String code = crypto.otp();
        String hash = transactions.otpHash(email, purpose, code);
        store.putOtp(email, purpose, hash, transactions.context(account));
        try { mail.send(email, code, purpose); }
        catch (MailException ex) {
            store.cancelOtp(email, purpose, hash);
            log.warn("OTP email delivery failed; purpose={}", purpose);
            if (reportFailure) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
                    "Account created but email delivery failed; request a new verification code later");
        }
    }

    @Override
    public void resetPassword(AuthRequests.ResetPassword request) {
        transactions.reset(request.email(), request.otp(), request.newPassword());
    }

    @Override
    public IssuedTokens login(AuthRequests.Login request, String ip) {
        store.checkLoginLimit(request.email(), ip);
        AppAccount account = accounts.findByEmail(request.email()).orElse(null);
        boolean matches = request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (!matches || account == null) {
            store.loginFailed(request.email(), ip);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "Account is not active");
        }
        if (account.getEmailVerifiedAt() == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verify your email before logging in");
        }
        if (Boolean.TRUE.equals(account.getMustChangePassword())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "PASSWORD_RESET_REQUIRED", "Reset your password before logging in");
        }
        var session = new AuthSession(UUID.randomUUID().toString(), account.getId(),
                crypto.passwordFingerprint(account.getPasswordHash()), clock.instant().plus(properties.sessionTtl()));
        String token = crypto.token();
        store.createSession(session, crypto.digest(token));
        return issued(session, account, token);
    }

    @Override
    public IssuedTokens refresh(String token) {
        validateToken(token);
        String hash = crypto.digest(token);
        AuthSession session = store.sessionForRefresh(hash);
        AppAccount account = sessionAccounts.validate(session);
        String next = crypto.token();
        store.rotate(session, hash, crypto.digest(next));
        return issued(session, account, next);
    }

    private IssuedTokens issued(AuthSession session, AppAccount account, String refresh) {
        var response = sessionAccounts.response(account);
        long ttl = Math.min(properties.accessTtl().toSeconds(), Duration.between(clock.instant(), session.expiresAt()).toSeconds());
        return new IssuedTokens(new AuthResponse(jwt.issue(session, response), "Bearer", ttl, response), refresh, session.expiresAt());
    }

    @Override
    public void logout(String token) {
        if (token != null && token.matches("[A-Za-z0-9_-]{43}")) store.logout(crypto.digest(token));
    }

    private void validateToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw AuthException.invalidSession();
    }
}
