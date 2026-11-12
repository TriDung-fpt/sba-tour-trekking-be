package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateLocationRequest(@NotBlank @Size(max = 200) String name,
                                    @Size(max = 150) String provinceName,
                                    @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                    @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
