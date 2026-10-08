package com.trekking.app;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.account.dto.request.PasswordValidator;
import com.trekking.app.features.account.repository.RedisAuthStore;
import com.trekking.app.features.account.service.session.AuthSession;
import com.trekking.app.features.account.enums.OtpPurpose;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSecurityTests {
    private AuthProperties properties() {
        return new AuthProperties("test-jwt-key-012345678901234567890123456789",
                "test-otp-key-012345678901234567890123456789", "test", "test",
                Duration.ofMinutes(15), Duration.ofDays(7), true, List.of(), "test@example.invalid", "test:");
    }

    @Test
    void redisFailureDoesNotIssueOrAcceptAuthenticationState() {
        var template = mock(StringRedisTemplate.class, invocation -> { throw new RedisConnectionFailureException("Unavailable"); });
        var store = new RedisAuthStore(template, new AuthCrypto(properties()), properties());
        List<Runnable> operations = List.of(
                () -> store.getSession(UUID.randomUUID().toString()),
                () -> store.sessionForRefresh("hash"),
                () -> store.checkLoginLimit("test@example.invalid", "127.0.0.1"),
                () -> store.reserveEmail("test@example.invalid", OtpPurpose.VERIFY_EMAIL),
                () -> store.createSession(new AuthSession(UUID.randomUUID().toString(), 1L, "hash", Instant.now().plusSeconds(60)), "refresh"));
        for (Runnable operation : operations) {
            assertThatThrownBy(operation::run).isInstanceOfSatisfying(AuthException.class,
                    ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        }
    }

    @Test
    void passwordLengthCountsUtf8BytesAndDoesNotTrim() {
        var validator = new PasswordValidator();
        assertThat(validator.isValid("1234567", null)).isFalse();
        assertThat(validator.isValid(" ".repeat(8), null)).isFalse();
        assertThat(validator.isValid("a".repeat(72), null)).isTrue();
        assertThat(validator.isValid("a".repeat(73), null)).isFalse();
        assertThat(validator.isValid("ễ".repeat(25), null)).isFalse();
        assertThat(validator.isValid(" password ", null)).isTrue();
    }

    @Test
    void weakOrSharedSecretsFailConfiguration() {
        assertThatThrownBy(() -> new AuthProperties("short", "short", "test", "test", Duration.ofMinutes(15),
                Duration.ofDays(7), true, List.of(), "", "test:")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthProperties("a".repeat(32), "a".repeat(32), "test", "test", Duration.ofMinutes(15),
                Duration.ofDays(7), true, List.of(), "", "test:")).isInstanceOf(IllegalArgumentException.class);
    }
}
