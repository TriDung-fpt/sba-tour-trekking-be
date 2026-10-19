package com.trekking.app;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.core.security.SessionJwtAuthenticationConverter;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.enums.AccountStatus;
import com.trekking.app.features.auth.enums.OtpPurpose;
import com.trekking.app.features.auth.repository.RedisAuthStore;
import com.trekking.app.features.auth.service.impl.SessionAccounts;
import com.trekking.app.features.auth.model.AuthSession;
import com.trekking.app.features.auth.validation.AuthValidation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthValidationTests {
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private static final String EMAIL = "test@example.invalid";
    private static final String IP = "127.0.0.1";
    private final RedisAuthStore store = mock(RedisAuthStore.class);
    private final AuthCrypto crypto = new AuthCrypto(properties());
    private final AuthValidation validation = new AuthValidation(store, crypto, Clock.fixed(NOW, ZoneOffset.UTC));

    private static AuthProperties properties() {
        return new AuthProperties("test-jwt-key-012345678901234567890123456789",
                "test-otp-key-012345678901234567890123456789", "test", "test",
                Duration.ofMinutes(15), Duration.ofDays(7), true, List.of(), "test@example.invalid", "test:");
    }

    @Test
    void atomicOtpFailureIsTranslatedByValidationAndNotByRepository() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);
        var repository = new RedisAuthStore(redis, crypto, properties());
        assertThat(repository.consumeOtp(EMAIL, OtpPurpose.VERIFY_EMAIL, "hash", "context")).isFalse();
        assertThatThrownBy(() -> validation.consumeOtp(EMAIL, OtpPurpose.VERIFY_EMAIL, "hash", "context"))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_OTP"));
    }

    @Test
    void failedLoginPolicyUsesBothCounterLimits() {
        when(store.loginAttempts(EMAIL, IP)).thenReturn(new RedisAuthStore.LoginAttempts(9, 99));
        assertThatCode(() -> validation.checkLoginLimit(EMAIL, IP)).doesNotThrowAnyException();
        when(store.loginAttempts(EMAIL, IP)).thenReturn(new RedisAuthStore.LoginAttempts(10, 0));
        assertLoginLimit();
        when(store.loginAttempts(EMAIL, IP)).thenReturn(new RedisAuthStore.LoginAttempts(0, 100));
        assertLoginLimit();
    }

    private void assertLoginLimit() {
        assertThatThrownBy(() -> validation.checkLoginLimit(EMAIL, IP)).isInstanceOfSatisfying(AuthException.class,
                ex -> assertThat(ex.getCode()).isEqualTo("TOO_MANY_ATTEMPTS"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unavailableOrMalformedRedisCountersFailClosedAsInfrastructureErrors() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var repository = new RedisAuthStore(redis, crypto, properties());
        var policy = new AuthValidation(repository, crypto, Clock.fixed(NOW, ZoneOffset.UTC));
        when(values.multiGet(anyCollection())).thenReturn(null);
        assertThatThrownBy(() -> policy.checkLoginLimit(EMAIL, IP)).isInstanceOf(DataAccessResourceFailureException.class);
        when(values.multiGet(anyCollection())).thenReturn(List.of("invalid", "0"));
        assertThatThrownBy(() -> policy.checkLoginLimit(EMAIL, IP)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingSessionIsAnEmptyRepositoryLookupAndAnInvalidAuthenticationState() {
        var redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of());
        var repository = new RedisAuthStore(redis, crypto, properties());
        assertThat(repository.getSession("arbitrary-persistence-key")).isNull();
        assertThat(repository.getSession(null)).isNull();
        assertThatThrownBy(() -> validation.getSession(UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(AuthException.class, ex -> assertThat(ex.getCode()).isEqualTo("INVALID_SESSION"));
    }

    @Test
    void malformedSessionIdsAreRejectedBeforeRedisLookup() {
        assertThatThrownBy(() -> validation.getSession("-".repeat(36))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> validation.getSession(null)).isInstanceOf(AuthException.class);
        verifyNoInteractions(store);
    }

    @Test
    void refreshTokensMustHaveTheExpectedOpaqueTokenFormat() {
        assertThatCode(() -> validation.validateRefreshToken(crypto.token())).doesNotThrowAnyException();
        assertThat(validation.isRefreshToken(null)).isFalse();
        assertThatThrownBy(() -> validation.validateRefreshToken("malformed token")).isInstanceOf(AuthException.class);
    }

    @Test
    void sessionAtExpiryAndAfterPasswordChangeIsRevoked() {
        var account = activeAccount();
        var expired = session(account, NOW);
        assertThatThrownBy(() -> validation.validateSession(expired, account)).isInstanceOf(AuthException.class);
        verify(store).revoke(expired.id());

        var session = session(account, NOW.plusSeconds(60));
        account.setPasswordHash("changed password hash");
        assertThatThrownBy(() -> validation.validateSession(session, account)).isInstanceOf(AuthException.class);
        verify(store).revoke(session.id());
    }

    @Test
    void deletedOrBlockedAccountsCannotKeepASession() {
        var account = activeAccount();
        var session = session(account, NOW.plusSeconds(60));
        assertThatCode(() -> validation.validateSession(session, account)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validation.validateSession(session, null)).isInstanceOf(AuthException.class);
        account.setStatus(AccountStatus.BLOCKED);
        assertThatThrownBy(() -> validation.validateSession(session, account)).isInstanceOf(AuthException.class);
        verify(store, times(2)).revoke(session.id());
    }

    @Test
    void atomicSessionCreationAndRotationFailuresPreventTokenIssuance() {
        var session = session(activeAccount(), NOW.plusSeconds(60));
        assertThatThrownBy(() -> validation.createSession(session, "hash")).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> validation.rotate(session, "old", "new")).isInstanceOf(AuthException.class);
    }

    @Test
    void jwtConverterDistinguishesMissingSessionFromInfrastructureFailure() {
        var accounts = mock(SessionAccounts.class);
        var converter = new SessionJwtAuthenticationConverter(validation, accounts);
        String id = UUID.randomUUID().toString();
        var jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("1").claim("sid", id).build();
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(BadCredentialsException.class);
        when(store.getSession(id)).thenThrow(new DataAccessResourceFailureException("Unavailable"));
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(AuthenticationServiceException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void requestNormalizationKeepsPasswordsUnchanged() {
        var request = new AuthRequests.Register("  TeSt@Example.invalid  ", " password ", "  Test User  ", null);
        assertThat(request.email()).isEqualTo(EMAIL);
        assertThat(request.fullName()).isEqualTo("Test User");
        assertThat(request.password()).isEqualTo(" password ");
    }

    private AppAccount activeAccount() {
        var account = new AppAccount();
        account.setPasswordHash("original password hash");
        account.setEmailVerifiedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        return account;
    }

    private AuthSession session(AppAccount account, Instant expires) {
        return new AuthSession(UUID.randomUUID().toString(), 1L, crypto.passwordFingerprint(account.getPasswordHash()), expires);
    }
}
