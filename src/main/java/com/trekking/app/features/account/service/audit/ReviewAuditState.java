package com.trekking.app.features.account.service.audit;

/** Allowlisted review moderation fields for audit details. */
public record ReviewAuditState(String status, String moderationReason, Long moderatedBy) {
}
