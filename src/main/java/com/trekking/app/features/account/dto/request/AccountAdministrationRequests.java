package com.trekking.app.features.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AccountAdministrationRequests {
    private AccountAdministrationRequests() {}

    public record Reason(@NotBlank @Size(max = 2000) String reason) {
        public Reason { reason = normalize(reason); }
    }

    public record Roles(@NotEmpty @Size(max = 6) List<@NotBlank String> roles,
                        @NotBlank @Size(max = 2000) String reason) {
        public Roles { reason = normalize(reason); }
    }

    private static String normalize(String reason) {
        return reason == null ? null : reason.strip();
    }
}
