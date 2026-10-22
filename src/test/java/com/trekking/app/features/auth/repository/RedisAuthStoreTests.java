package com.trekking.app.features.auth.repository;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.enums.OtpPurpose;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisAuthStoreTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisAuthStore store = new RedisAuthStore(redis, new AuthCrypto(properties()), properties());

    @Test
    @SuppressWarnings("unchecked")
    void absentLoginCountersMeanNoAttemptsButMalformedCountersFailClosed() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyList())).thenReturn(Arrays.asList(null, "3"));
        assertThat(store.loginAttempts("customer@example.invalid", "127.0.0.1"))
                .isEqualTo(new RedisAuthStore.LoginAttempts(0, 3));
        when(values.multiGet(anyList())).thenReturn(List.of("invalid", "3"));
        assertThatThrownBy(() -> store.loginAttempts("customer@example.invalid", "127.0.0.1"))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasCauseInstanceOf(NumberFormatException.class);
        when(values.multiGet(anyList())).thenReturn(List.of("0"));
        assertThatThrownBy(() -> store.loginAttempts("customer@example.invalid", "127.0.0.1"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingSessionIsEmptyWhileMalformedSessionIsAnInfrastructureFailure() {
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries("test:session:session-id")).thenReturn(Map.of());
        assertThat(store.getSession("session-id")).isNull();
        when(hashes.entries("test:session:session-id"))
                .thenReturn(Map.of("account", "invalid", "fingerprint", "hash", "expires", "2000000000"));
        assertThatThrownBy(() -> store.getSession("session-id"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void scriptMustReturnAnExplicitSuccessBeforeAnOtpCanBeConsumed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertThat(store.consumeOtp("customer@example.invalid", OtpPurpose.VERIFY_EMAIL, "hash", "context")).isFalse();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        assertThat(store.consumeOtp("customer@example.invalid", OtpPurpose.VERIFY_EMAIL, "hash", "context")).isTrue();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(null);
        assertThatThrownBy(() -> store.consumeOtp("customer@example.invalid", OtpPurpose.VERIFY_EMAIL, "hash", "context"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisOutageCannotBeTreatedAsOtpApproval() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("Unavailable"));
        assertThatThrownBy(() -> store.consumeOtp("customer@example.invalid", OtpPurpose.VERIFY_EMAIL, "hash", "context"))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    private static AuthProperties properties() {
        return new AuthProperties("test-jwt-key-012345678901234567890123456789",
                "test-otp-key-012345678901234567890123456789", "test", "test",
                Duration.ofMinutes(15), Duration.ofDays(7), true, List.of(), "test@example.invalid", "test:");
    }
}