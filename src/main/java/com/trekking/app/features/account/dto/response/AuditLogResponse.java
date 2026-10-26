package com.trekking.app.features.account.dto.response;

import java.time.OffsetDateTime;

public record AuditLogResponse(Long id, Long actorId, String action, String entityType, Long entityId,
                               String reason, String details, OffsetDateTime occurredAt) {
}
