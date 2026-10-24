package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PolicyCreateRequest(@NotBlank @Size(max = 80) String code,
                                  @NotBlank @Size(max = 250) String title,
                                  @NotBlank @Pattern(regexp = "cancellation|payment|general") String kind) {}
