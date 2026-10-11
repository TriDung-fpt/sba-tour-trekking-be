package com.trekking.app.features.account.dto.request;

import com.trekking.app.features.account.validation.AuthInputNormalizer;
import com.trekking.app.features.account.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record Register(@NotBlank @Email @Size(max = 254) String email,
                           @ValidPassword String password,
                           @NotBlank @Size(max = 200) String fullName,
                           @Size(max = 30) String phone) {
        public Register { email = AuthInputNormalizer.email(email); fullName = AuthInputNormalizer.fullName(fullName); }
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 256) String password) {
        public Login { email = AuthInputNormalizer.email(email); }
    }

    public record GoogleLogin(@NotBlank @Size(max = 16384) String idToken) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
        public EmailRequest { email = AuthInputNormalizer.email(email); }
    }

    public record VerifyEmail(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
        public VerifyEmail { email = AuthInputNormalizer.email(email); }
    }

    public record ResetPassword(@NotBlank @Email @Size(max = 254) String email,
                                @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
                                @ValidPassword String newPassword) {
        public ResetPassword { email = AuthInputNormalizer.email(email); }
    }
}
