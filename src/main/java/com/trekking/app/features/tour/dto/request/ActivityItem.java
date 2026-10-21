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

public record ActivityItem(@Min(1) int dayNumber, @Min(0) int displayOrder,
                               @NotBlank @Size(max = 250) String title, @Size(max = 5000) String description,
                               LocalTime startTime, @Positive Long locationId) {}
