package com.trekking.app.features.company.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PolicyPublishRequest(@NotBlank @Size(max = 100000) String content,
                                   @NotNull OffsetDateTime effectiveFrom,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal platformFeePercent,
                                   @NotNull @Min(0) Integer payoutWaitHours,
                                   @NotBlank @Size(max = 100000) String payoutTermsSnapshot) {}
