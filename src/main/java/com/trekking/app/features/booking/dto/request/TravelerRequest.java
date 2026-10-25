package com.trekking.app.features.booking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TravelerRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Email @Size(max = 254) String email,
        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[\\d\\s().-]{7,30}$") String phone) {}
