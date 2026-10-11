package com.trekking.app.features.auth.repository;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.model.AuthSession;
import com.trekking.app.features.auth.enums.OtpPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class RedisAuthStore {
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final String prefix;

    public RedisAuthStore(StringRedisTemplate redis, AuthCrypto crypto, AuthProperties properties) {
        this.redis = redis;
        this.crypto = crypto;
        prefix = properties.redisPrefix();
    }

    private <T> T execute(Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException ex) { throw AuthException.unavailable(); }
    }

    private long script(String name, List<String> keys, String... args) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(Long.class);
        Long result = execute(() -> redis.execute(script, keys, (Object[]) args));
        if (result == null) throw AuthException.unavailable();
        return result;
    }

    private String otpKey(String email, OtpPurpose purpose) {
        return prefix + "otp:" + purpose + ":" + crypto.hmac("email:" + email);
    }

    public boolean reserveEmail(String email, OtpPurpose purpose) {
        String key = otpKey(email, purpose);
        return script("reserve-email", List.of(key + ":cooldown", key + ":hour")) == 1;
    }

    public void putOtp(String email, OtpPurpose purpose, String hash, String context) {
        script("put-otp", List.of(otpKey(email, purpose)), hash, context);
    }

    public void cancelOtp(String email, OtpPurpose purpose, String hash) {
        script("cancel-otp", List.of(otpKey(email, purpose)), hash);
    }

    public void consumeOtp(String email, OtpPurpose purpose, String hash, String context) {
        if (script("consume-otp", List.of(otpKey(email, purpose)), hash, context) != 1) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "OTP is invalid or expired");
        }
    }

    private String loginKey(String kind, String value) { return prefix + "login:" + kind + ":" + crypto.hmac(value); }

    public void checkLoginLimit(String email, String ip) {
        List<String> values = execute(() -> redis.opsForValue().multiGet(List.of(loginKey("email", email), loginKey("ip", ip))));
        if (values != null && (number(values.get(0)) >= 10 || number(values.get(1)) >= 100)) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many attempts; try again later");
        }
    }

    private long number(String value) { return value == null ? 0 : Long.parseLong(value); }

    public void loginFailed(String email, String ip) {
        script("login-failed", List.of(loginKey("email", email), loginKey("ip", ip)));
    }

    public void createSession(AuthSession session, String tokenHash) {
        long result = script("create-session", List.of(prefix + "session:" + session.id(), prefix + "refresh:" + tokenHash),
                Long.toString(session.accountId()), session.passwordFingerprint(), Long.toString(session.expiresAt().getEpochSecond()),
                tokenHash, session.id());
        if (result != 1) throw AuthException.invalidSession();
    }

    public AuthSession getSession(String id) {
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw AuthException.invalidSession();
        Map<Object, Object> data = execute(() -> redis.opsForHash().entries(prefix + "session:" + id));
        if (data.isEmpty()) throw AuthException.invalidSession();
        return new AuthSession(id, Long.parseLong((String) data.get("account")), (String) data.get("fingerprint"),
                Instant.ofEpochSecond(Long.parseLong((String) data.get("expires"))));
    }

    public AuthSession sessionForRefresh(String hash) {
        String id = execute(() -> redis.opsForValue().get(prefix + "refresh:" + hash));
        return getSession(id);
    }

    public void rotate(AuthSession session, String oldHash, String newHash) {
        if (script("rotate-session", List.of(prefix + "session:" + session.id(), prefix + "refresh:" + oldHash,
                prefix + "refresh:" + newHash), oldHash, newHash, session.id()) != 1) {
            throw AuthException.invalidSession();
        }
    }

    public void revoke(String id) { execute(() -> redis.delete(prefix + "session:" + id)); }

    public void logout(String hash) {
        String id = execute(() -> redis.opsForValue().get(prefix + "refresh:" + hash));
        if (id != null) revoke(id);
    }
}
