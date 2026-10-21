package com.trekking.app.features.company.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;

@Schema(description = "Partner business application")
public record CompanyApplicationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String contactName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 250) String businessName,
        @NotBlank @Size(max = 50) String taxCode,
        @Pattern(regexp = "company|household|other") String businessType,
        @Min(1900) @Max(2100) Integer foundedYear,
        @Size(max = 1000) String address,
        @Size(max = 1000) String description,
        @Size(max = 5) List<@Pattern(regexp = "fansipan|hagiang|bachmoc|langbiang|phongnha") String> tourRegions,
        @NotNull @AssertTrue Boolean safetyRulesAccepted) {
    public CompanyApplicationRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        contactName = contactName == null ? null : contactName.trim();
        businessName = businessName == null ? null : businessName.trim();
        taxCode = taxCode == null ? null : taxCode.trim();
        phone = phone == null || phone.isBlank() ? null : phone.trim();
        address = address == null || address.isBlank() ? null : address.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        tourRegions = tourRegions == null ? List.of() : List.copyOf(tourRegions);
    }
}
