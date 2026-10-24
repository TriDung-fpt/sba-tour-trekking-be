package com.trekking.app.features.invitation.dto.request;

import com.trekking.app.features.auth.dto.request.ValidPassword;
import com.trekking.app.features.auth.validation.AuthInputNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InvitationRequests {
    private InvitationRequests() {}

    public record Staff(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 200) String fullName,
                        @Size(max = 30) String phone,
                        @NotBlank @Pattern(regexp = "admin") String role) {
        public Staff { email = AuthInputNormalizer.email(email); fullName = AuthInputNormalizer.fullName(fullName); }
    }

    public record Guide(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 200) String fullName,
                        @Size(max = 30) String phone,
                        @Size(max = 10000) String bio) {
        public Guide { email = AuthInputNormalizer.email(email); fullName = AuthInputNormalizer.fullName(fullName); }
    }

    public record Activate(@NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
                           @ValidPassword String newPassword) {
        public Activate { email = AuthInputNormalizer.email(email); }
    }
}
