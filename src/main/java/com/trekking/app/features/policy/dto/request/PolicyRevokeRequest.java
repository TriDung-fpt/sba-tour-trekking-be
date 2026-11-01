package com.trekking.app.features.policy.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PolicyRevokeRequest(@NotBlank @Size(max = 1000) String reason) {}
