package com.trekking.app.features.account.dto.response;

import java.util.List;

public record AuthorizationResponse(long accountId, List<String> roles, List<String> permissions) {
    public AuthorizationResponse {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

}
