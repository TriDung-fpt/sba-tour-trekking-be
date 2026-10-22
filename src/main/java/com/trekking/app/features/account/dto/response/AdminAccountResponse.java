package com.trekking.app.features.account.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminAccountResponse(Long id, String email, String fullName, String phone, List<String> roles,
                                   String status, String blockedReason, Long blockedBy,
                                   boolean mustChangePassword, OffsetDateTime emailVerifiedAt) {
    public AdminAccountResponse { roles = List.copyOf(roles); }
}
