package com.trekking.app.core.common.idempotency;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

@Component
public class AuditedIdempotency {
    private final EntityManager entityManager;
    private final AuditLogRepository audits;
    private final ObjectMapper mapper;

    public AuditedIdempotency(EntityManager entityManager, AuditLogRepository audits, ObjectMapper mapper) {
        this.entityManager = entityManager;
        this.audits = audits;
        this.mapper = mapper;
    }

    public Optional<AuditLog> begin(String action, String key, Object request) {
        if (!StringUtils.hasText(key) || key.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-100 characters");
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", action + ":" + key).getSingleResult();
        var prior = audits.findIdempotent(action, key);
        if (prior.isPresent() && !hash(request).equals(readRequestHash(prior.get())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
        return prior;
    }

    public AuditLog record(long actorId, String action, String entityType, long entityId,
            String reason, String key, Object request) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setReason(reason);
        log.setDetails(json(key, hash(request)));
        log.setOccurredAt(OffsetDateTime.now());
        return audits.save(log);
    }

    private String readRequestHash(AuditLog log) {
        try { return mapper.readTree(log.getDetails()).path("requestHash").asString(); }
        catch (RuntimeException ex) { return ""; }
    }

    private String hash(Object value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) { throw new IllegalStateException("Unable to fingerprint finance request", ex); }
    }

    private String json(String key, String requestHash) {
        try { return mapper.writeValueAsString(java.util.Map.of("idempotencyKey", key, "requestHash", requestHash)); }
        catch (Exception ex) { throw new IllegalStateException("Unable to serialize audit metadata", ex); }
    }
}
