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
        var audit = new AuditLog();
        audit.setActorId(actorId);
        audit.setEntityType("account");
        audit.setEntityId(accountId);
        audit.setAction(action.name());
        audit.setReason(reason);
        audit.setDetails(json.writeValueAsString(Map.of("before", before, "after", after)));
        audit.setOccurredAt(OffsetDateTime.now(clock));
        repository.saveAndFlush(audit);
    }
}
