package com.trekking.app.features.auth.repository;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.auth.model.AuthSession;
import com.trekking.app.features.auth.enums.OtpPurpose;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private long script(String name, List<String> keys, String... args) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(Long.class);
        Long result = redis.execute(script, keys, (Object[]) args);
        if (result == null) throw new DataAccessResourceFailureException("Redis script returned no result");
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

    public boolean consumeOtp(String email, OtpPurpose purpose, String hash, String context) {
        return script("consume-otp", List.of(otpKey(email, purpose)), hash, context) == 1;
    }

    public void putPartnerActivation(String tokenHash, long accountId) {
        execute(() -> {
            redis.opsForValue().set(prefix + "partner-activation:" + tokenHash,
                    Long.toString(accountId), java.time.Duration.ofHours(24));
            return true;
        });
    }

    public Long consumePartnerActivation(String tokenHash) {
        var script = new DefaultRedisScript<String>();
        script.setScriptText("local value = redis.call('GET', KEYS[1]); if value then redis.call('DEL', KEYS[1]); end; return value");
        script.setResultType(String.class);
        String accountId = execute(() -> redis.execute(script, List.of(prefix + "partner-activation:" + tokenHash)));
        try { return accountId == null ? null : Long.valueOf(accountId); }
        catch (NumberFormatException ex) { throw AuthException.unavailable(); }
    }

    private String loginKey(String kind, String value) { return prefix + "login:" + kind + ":" + crypto.hmac(value); }

    public LoginAttempts loginAttempts(String email, String ip) {
        List<String> values = redis.opsForValue().multiGet(List.of(loginKey("email", email), loginKey("ip", ip)));
        if (values == null || values.size() != 2) {
            throw new DataAccessResourceFailureException("Redis returned incomplete login counters");
        }
        return new LoginAttempts(number(values.get(0)), number(values.get(1)));
    }

    public record LoginAttempts(long email, long ip) {}

    private long number(String value) {
        try { return value == null ? 0 : Long.parseLong(value); }
        catch (NumberFormatException ex) {
            throw new DataAccessResourceFailureException("Redis returned an invalid login counter", ex);
        }
    }

    public void loginFailed(String email, String ip) {
        script("login-failed", List.of(loginKey("email", email), loginKey("ip", ip)));
    }

    public boolean createSession(AuthSession session, String tokenHash) {
        return script("create-session", List.of(prefix + "session:" + session.id(), prefix + "refresh:" + tokenHash),
                Long.toString(session.accountId()), session.passwordFingerprint(), Long.toString(session.expiresAt().getEpochSecond()),
                tokenHash, session.id()) == 1;
    }

    public AuthSession getSession(String id) {
        if (id == null) return null;
        Map<Object, Object> data = redis.opsForHash().entries(prefix + "session:" + id);
        if (data == null) throw new DataAccessResourceFailureException("Redis returned no session data");
        if (data.isEmpty()) return null;
        try {
            String fingerprint = (String) data.get("fingerprint");
            if (fingerprint == null) throw new IllegalArgumentException("Missing session fingerprint");
            return new AuthSession(id, Long.parseLong((String) data.get("account")), fingerprint,
                    Instant.ofEpochSecond(Long.parseLong((String) data.get("expires"))));
        } catch (IllegalArgumentException | ClassCastException | java.time.DateTimeException ex) {
            throw new DataAccessResourceFailureException("Redis returned invalid session data", ex);
        }
    }

    public AuthSession sessionForRefresh(String hash) {
        String id = redis.opsForValue().get(prefix + "refresh:" + hash);
        return getSession(id);
    }

    public boolean rotate(AuthSession session, String oldHash, String newHash) {
        return script("rotate-session", List.of(prefix + "session:" + session.id(), prefix + "refresh:" + oldHash,
                prefix + "refresh:" + newHash), oldHash, newHash, session.id()) == 1;
    }

    public void revoke(String id) { redis.delete(prefix + "session:" + id); }

    public void logout(String hash) {
        String id = redis.opsForValue().get(prefix + "refresh:" + hash);
        if (id != null) revoke(id);
    }
}
