package com.trekking.app.features.account.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class AuthRequests {
    private AuthRequests() {}

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Register(@NotBlank @Email @Size(max = 254) String email,
                           @ValidPassword String password,
                           @NotBlank @Size(max = 200) String fullName,
                           @Size(max = 30) String phone) {
        public Register { email = normalize(email); fullName = fullName == null ? null : fullName.trim(); }
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 256) String password) {
        public Login { email = normalize(email); }
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
        public EmailRequest { email = normalize(email); }
    }

    public record VerifyEmail(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
        public VerifyEmail { email = normalize(email); }
    }

    public record ResetPassword(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
                                @ValidPassword String newPassword) {
        public ResetPassword { email = normalize(email); }
    }
}
