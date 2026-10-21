package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

public record PackingItem(@NotBlank @Size(max = 200) String name, boolean required,
                              @Size(max = 2000) String note, @Min(0) @Max(1000) int displayOrder) {}
