package com.trekking.app.features.invitation.repository;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.AuthCrypto;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisInvitationStore {
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final String prefix;

    public RedisInvitationStore(StringRedisTemplate redis, AuthCrypto crypto, AuthProperties properties) {
        this.redis = redis;
        this.crypto = crypto;
        this.prefix = properties.redisPrefix() + "invitation:activate:";
    }

    private String key(String email) { return prefix + crypto.hmac("email:" + email); }

    private long script(String name, List<String> keys, String... args) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("redis/invitation-" + name + ".lua"));
        script.setResultType(Long.class);
        Long result = redis.execute(script, keys, (Object[]) args);
        if (result == null) throw new DataAccessResourceFailureException("Redis script returned no result");
        return result;
    }

    public boolean issue(String email, String hash, String context) {
        String key = key(email);
        return script("issue", List.of(key, key + ":cooldown", key + ":hour"), hash, context) == 1;
    }

    public boolean consume(String email, String hash, String context) {
        return script("consume", List.of(key(email)), hash, context) == 1;
    }

    public void cancel(String email, String hash) { script("cancel", List.of(key(email)), hash); }
}
