package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record EquipmentPackage(@NotBlank @Size(max = 200) String title,
                                   @NotBlank @Size(max = 10000) String includedItems,
                                   @Size(max = 10000) String sharedItems,
                                   @Size(max = 10000) String personalItemsToBring) {}
