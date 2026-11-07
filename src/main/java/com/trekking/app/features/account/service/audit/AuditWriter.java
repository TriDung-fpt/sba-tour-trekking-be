package com.trekking.app.features.account.service.audit;

import com.trekking.app.features.account.entity.AuditLog;
import com.trekking.app.features.account.repository.AuditLogRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditWriter {
    public enum AccountAction { ACCOUNT_BLOCKED, ACCOUNT_UNBLOCKED, ACCOUNT_ROLES_CHANGED }
    public enum ReviewAction { REVIEW_HIDDEN, REVIEW_RESTORED }

    private final AuditLogRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditWriter(AuditLogRepository repository, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accountChanged(Long actorId, Long accountId, AccountAction action, String reason,
                               AccountAuditState before, AccountAuditState after) {
        write(actorId, "account", accountId, action.name(), reason, Map.of("before", before, "after", after));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reviewChanged(Long actorId, Long reviewId, ReviewAction action, String reason,
                              ReviewAuditState before, ReviewAuditState after) {
        write(actorId, "review", reviewId, action.name(), reason, Map.of("before", before, "after", after));
    }

    private void write(Long actorId, String entityType, Long entityId, String action, String reason, Map<?, ?> details) {
        var audit = new AuditLog();
        audit.setActorId(actorId);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setAction(action);
        audit.setReason(reason);
        audit.setDetails(json.writeValueAsString(details));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        repository.saveAndFlush(audit);
    }
}
