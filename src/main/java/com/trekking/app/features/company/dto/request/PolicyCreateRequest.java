package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PolicyCreateRequest(@NotBlank @Size(max = 80) String code,
                                  @NotBlank @Size(max = 250) String title,
                                  @Positive Long companyId) {}
