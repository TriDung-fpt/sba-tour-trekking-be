package com.trekking.app.features.policy.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PolicyCreateWithVersionRequest(@NotNull @Valid PolicyCreateRequest policy,
                                             @NotNull @Valid PolicyPublishRequest version) {}
