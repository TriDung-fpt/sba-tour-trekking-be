package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record AgreementAcceptRequest(@NotNull @Positive Long policyVersionId,
                                     @NotNull @Pattern(regexp = "[0-9]{6}") String otp) {}
