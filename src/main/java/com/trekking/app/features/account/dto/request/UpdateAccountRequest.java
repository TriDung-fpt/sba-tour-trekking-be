package com.trekking.app.features.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Editable fields for the authenticated account. Email and roles are read-only.")
public record UpdateAccountRequest(
        @Schema(description = "Account holder's full name", example = "Nguyen Minh Anh")
        @NotBlank @Size(max = 200) String fullName,
        @Schema(description = "Phone number; an empty string clears the saved number", example = "+84901234567", maxLength = 30)
        @Size(max = 30) String phone) {
    public UpdateAccountRequest {
        fullName = fullName == null ? null : fullName.trim();
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
