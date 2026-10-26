package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePassengerRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 30) @Pattern(regexp = "^[0-9+() .-]*$", message = "must be a valid phone number") String phone,
        @Email @Size(max = 254) String email
) {}
