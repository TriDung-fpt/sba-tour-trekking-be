package com.trekking.app.features.account.service.auth;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.core.security.JwtService;
import com.trekking.app.features.account.dto.request.AuthRequests;
import com.trekking.app.features.account.dto.response.AuthResponse;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.repository.RedisAuthStore;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import com.trekking.app.features.account.service.account.AccountTransactions;
import com.trekking.app.features.account.service.account.GoogleAccountTransactions;
import com.trekking.app.features.account.service.account.SessionAccounts;
import com.trekking.app.features.account.service.google.GoogleTokenVerifier;
import com.trekking.app.features.account.service.otp.OtpMailSender;
import com.trekking.app.features.account.validation.AuthInputNormalizer;
import com.trekking.app.features.account.validation.AuthValidation;
import com.trekking.app.features.account.enums.OtpPurpose;
import com.trekking.app.features.account.service.session.AuthSession;
import com.trekking.app.features.account.service.session.IssuedTokens;
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
    private final AuthValidation validation;
    private final GoogleTokenVerifier googleTokens;
    private final GoogleAccountTransactions googleAccounts;

    public AuthServiceImpl(AppAccountRepository accounts, AccountTransactions transactions, RedisAuthStore store,
                           AuthCrypto crypto, PasswordEncoder passwords, OtpMailSender mail, JwtService jwt,
                           SessionAccounts sessionAccounts, AuthProperties properties, Clock clock,
                           AuthValidation validation, GoogleTokenVerifier googleTokens,
                           GoogleAccountTransactions googleAccounts) {
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
        this.validation = validation;
        this.googleTokens = googleTokens;
        this.googleAccounts = googleAccounts;
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
        email = AuthInputNormalizer.email(email);
        if (!store.reserveEmail(email, purpose)) return;
        accounts.findByEmail(email).filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .filter(a -> purpose != OtpPurpose.VERIFY_EMAIL || a.getEmailVerifiedAt() == null)
                .ifPresent(a -> sendOtp(a, purpose, false));
    }

    private void sendOtp(AppAccount account, OtpPurpose purpose, boolean reportFailure) {
        String email = AuthInputNormalizer.email(account.getEmail());
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
        validation.checkLoginLimit(request.email(), ip);
        AppAccount account = accounts.findByEmail(request.email()).orElse(null);
        boolean matches = request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (!matches || account == null) {
            store.loginFailed(request.email(), ip);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        validation.validateLoginAccount(account);
        return startSession(account);
    }

    @Override
    public IssuedTokens loginGoogle(AuthRequests.GoogleLogin request, String ip) {
        // Verification happens before opening a database transaction.
        var identity = googleTokens.verify(request.idToken());
        validation.checkLoginLimit(identity.email(), ip);
        AppAccount account;
        try { account = googleAccounts.resolve(identity); }
        catch (DataIntegrityViolationException ex) {
            // Concurrent first logins can race on unique email/sub. The failed transaction
            // has rolled back before resolving again through the transactional proxy.
            if (accounts.findByGoogleSubject(identity.subject()).isEmpty()
                    && accounts.findByEmail(identity.email()).isEmpty()) throw ex;
            account = googleAccounts.resolve(identity);
        }
        return startSession(account);
    }

    private IssuedTokens startSession(AppAccount account) {
        var session = new AuthSession(UUID.randomUUID().toString(), account.getId(),
                crypto.passwordFingerprint(account.getPasswordHash()), clock.instant().plus(properties.sessionTtl()));
        String token = crypto.token();
        validation.createSession(session, crypto.digest(token));
        return issued(session, account, token);
    }

    @Override
    public IssuedTokens refresh(String token) {
        validation.validateRefreshToken(token);
        String hash = crypto.digest(token);
        AuthSession session = validation.sessionForRefresh(hash);
        AppAccount account = sessionAccounts.validate(session);
        String next = crypto.token();
        validation.rotate(session, hash, crypto.digest(next));
        return issued(session, account, next);
    }

    private IssuedTokens issued(AuthSession session, AppAccount account, String refresh) {
        var response = sessionAccounts.response(account);
        long ttl = Math.min(properties.accessTtl().toSeconds(), Duration.between(clock.instant(), session.expiresAt()).toSeconds());
        return new IssuedTokens(new AuthResponse(jwt.issue(session, response), "Bearer", ttl, response), refresh, session.expiresAt());
    }

    @Override
    public void logout(String token) {
        if (validation.isRefreshToken(token)) store.logout(crypto.digest(token));
    }
}
