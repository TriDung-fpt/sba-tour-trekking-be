package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PolicyStatusRequest(@NotNull Boolean active, @NotBlank @Size(max = 1000) String reason) {}
