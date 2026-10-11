package com.trekking.app.features.account.validation;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.enums.OtpPurpose;
import com.trekking.app.features.account.repository.RedisAuthStore;
import com.trekking.app.features.account.service.session.AuthSession;
import java.time.Clock;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthValidation {
    private static final Pattern REFRESH_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern SESSION_ID = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final long EMAIL_LOGIN_LIMIT = 10;
    private static final long IP_LOGIN_LIMIT = 100;
    private final RedisAuthStore store;
    private final AuthCrypto crypto;
    private final Clock clock;

    public AuthValidation(RedisAuthStore store, AuthCrypto crypto, Clock clock) {
        this.store = store;
        this.crypto = crypto;
        this.clock = clock;
    }

    public void consumeOtp(String email, OtpPurpose purpose, String hash, String context) {
        if (!store.consumeOtp(email, purpose, hash, context)) throw invalidOtp();
    }

    public AppAccount validateOtpAccount(AppAccount account) {
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) throw invalidOtp();
        return account;
    }

    private AuthException invalidOtp() {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "OTP is invalid or expired");
    }

    public void checkLoginLimit(String email, String ip) {
        var attempts = store.loginAttempts(email, ip);
        if (attempts.email() >= EMAIL_LOGIN_LIMIT || attempts.ip() >= IP_LOGIN_LIMIT) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many attempts; try again later");
        }
    }

    public void validateLoginAccount(AppAccount account) {
        if (account == null) {
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
    }

    public void validateRefreshToken(String token) {
        if (!isRefreshToken(token)) throw AuthException.invalidSession();
    }

    public boolean isRefreshToken(String token) {
        return token != null && REFRESH_TOKEN.matcher(token).matches();
    }

    public AuthSession getSession(String id) {
        validateSessionId(id);
        return requireSession(store.getSession(id));
    }

    public AuthSession sessionForRefresh(String hash) {
        return requireSession(store.sessionForRefresh(hash));
    }

    private AuthSession requireSession(AuthSession session) {
        if (session == null) throw AuthException.invalidSession();
        validateSessionId(session.id());
        return session;
    }

    private void validateSessionId(String id) {
        if (id == null || !SESSION_ID.matcher(id).matches()) throw AuthException.invalidSession();
    }

    public void createSession(AuthSession session, String tokenHash) {
        if (!store.createSession(session, tokenHash)) throw AuthException.invalidSession();
    }

    public void rotate(AuthSession session, String oldHash, String newHash) {
        if (!store.rotate(session, oldHash, newHash)) throw AuthException.invalidSession();
    }

    public void validateSession(AuthSession session, AppAccount account) {
        if (session == null) throw AuthException.invalidSession();
        if (account == null || !session.expiresAt().isAfter(clock.instant())
                || account.getStatus() != AccountStatus.ACTIVE || account.getEmailVerifiedAt() == null
                || Boolean.TRUE.equals(account.getMustChangePassword())
                || !crypto.same(session.passwordFingerprint(), crypto.passwordFingerprint(account.getPasswordHash()))) {
            store.revoke(session.id());
            throw AuthException.invalidSession();
        }
    }
}
