package com.trekking.app.features.company.service.impl;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.AuthCrypto;
import com.trekking.app.features.company.service.AgreementChallengeService;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgreementChallengeServiceImpl implements AgreementChallengeService {
    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            if tonumber(redis.call('GET', KEYS[3]) or '0') >= 5 then return 0 end
            redis.call('SET', KEYS[2], '1', 'EX', 60)
            local count = redis.call('INCR', KEYS[3])
            if count == 1 then redis.call('EXPIRE', KEYS[3], 3600) end
            redis.call('HSET', KEYS[1], 'context', ARGV[1], 'hash', ARGV[2], 'attempts', 5)
            redis.call('EXPIRE', KEYS[1], 300)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'context') ~= ARGV[1] then return 0 end
            if redis.call('HGET', KEYS[1], 'hash') ~= ARGV[2] then
                local left = redis.call('HINCRBY', KEYS[1], 'attempts', -1)
                if left <= 0 then redis.call('DEL', KEYS[1]) end
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CANCEL = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'context') == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthCrypto crypto;
    private final String prefix;

    public AgreementChallengeServiceImpl(StringRedisTemplate redis, AuthCrypto crypto, AuthProperties properties) {
        this.redis = redis;
        this.crypto = crypto;
        this.prefix = properties.redisPrefix() + "agreement:";
    }

    @Override
    public void issue(long accountId, long companyId, String email, String context, String otp) {
        // Email limits are shared across every company and version in this purpose.
        String recipient = crypto.hmac(email.trim().toLowerCase(Locale.ROOT));
        Long result = redis.execute(ISSUE, List.of(key(accountId, companyId), prefix + "cooldown:" + recipient,
                prefix + "hour:" + recipient), context, hash(context, otp));
        if (!Long.valueOf(1).equals(result)) {
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "AGREEMENT_OTP_THROTTLED", "Wait before requesting another agreement code");
        }
    }

    @Override
    public void consume(long accountId, long companyId, String context, String otp) {
        Long result = redis.execute(CONSUME, List.of(key(accountId, companyId)), context, hash(context, otp));
        if (!Long.valueOf(1).equals(result)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_AGREEMENT_OTP", "Agreement code is invalid or expired; request a new code");
        }
    }

    @Override
    public void cancel(long accountId, long companyId, String context) {
        redis.execute(CANCEL, List.of(key(accountId, companyId)), context);
    }

    private String key(long accountId, long companyId) { return prefix + "challenge:" + accountId + ":" + companyId; }
    private String hash(String context, String otp) { return crypto.hmac("accept-company-agreement:" + context + ":" + otp); }
}
