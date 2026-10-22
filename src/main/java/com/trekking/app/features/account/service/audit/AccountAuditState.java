package com.trekking.app.features.account.service.audit;

import java.util.List;

/** Allowlisted audit fields; credentials, token state and account profile are excluded. */
public record AccountAuditState(String status, String blockedReason, Long blockedBy, List<String> roles) {
    public AccountAuditState { roles = List.copyOf(roles); }
}
