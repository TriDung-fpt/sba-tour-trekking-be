package com.trekking.app.features.account.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminAccountListItemResponse(Long id, String fullName, String email, String phone,
                                           List<String> roles, String status, OffsetDateTime createdAt,
                                           OffsetDateTime lastLoginAt) {
    public AdminAccountListItemResponse { roles = List.copyOf(roles); }
}
