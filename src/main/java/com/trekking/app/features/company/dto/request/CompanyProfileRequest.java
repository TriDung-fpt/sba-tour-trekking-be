package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyProfileRequest(
        @NotBlank @Size(max = 250) String name,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9-]+") String taxCode,
        @NotBlank @Size(max = 200) String representativeName,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Size(max = 30) String contactPhone,
        @NotBlank @Size(max = 2000) String address,
        @Size(max = 10000) String description) {}
