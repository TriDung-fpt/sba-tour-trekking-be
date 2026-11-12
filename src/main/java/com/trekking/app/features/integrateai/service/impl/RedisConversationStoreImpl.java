package com.trekking.app.features.integrateai.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.integrateai.config.IntegrateAiProperties;
import com.trekking.app.features.integrateai.service.ConversationStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RedisConversationStoreImpl implements ConversationStore {
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final ObjectMapper json;
    private final IntegrateAiProperties props;
    private final String prefix;

    public RedisConversationStoreImpl(StringRedisTemplate redis, AuthCrypto crypto, ObjectMapper json,
                                      IntegrateAiProperties props, AuthProperties auth) {
        this.redis = redis; this.crypto = crypto; this.json = json; this.props = props;
        this.prefix = auth.redisPrefix() + "integrate-ai:";
    }

    @Override
    public Conversation loadOrCreate(String requestedId, String owner) {
        String id = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString() : requestedId;
        if (!id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("Invalid session");
        var values = redis.opsForHash().entries(sessionKey(id));
        if (values.isEmpty()) return new Conversation(id, owner, List.of(), List.of());
        if (!owner.equals(values.get("owner"))) throw new IllegalArgumentException("Invalid session");
        try {
            Turn[] turns = json.readValue((String) values.get("turns"), Turn[].class);
            Long[] ids = json.readValue((String) values.get("tourIds"), Long[].class);
            return new Conversation(id, owner, List.of(turns), List.of(ids));
        } catch (RuntimeException ex) {
            redis.delete(sessionKey(id));
            return new Conversation(id, owner, List.of(), List.of());
        }
    }

    @Override
    public void save(Conversation conversation) {
        var key = sessionKey(conversation.id());
        redis.opsForHash().put(key, "owner", conversation.owner());
        redis.opsForHash().put(key, "turns", json.writeValueAsString(conversation.turns()));
        redis.opsForHash().put(key, "tourIds", json.writeValueAsString(conversation.recentTourIds()));
        redis.expire(key, props.session().ttl());
    }

    @Override
    public long incrementRate(String clientKey) {
        String key = prefix + "rate:" + crypto.hmac(clientKey);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, props.limits().rateWindow());
        return count == null ? Long.MAX_VALUE : count;
    }

    private String sessionKey(String id) { return prefix + "session:" + id; }
}
