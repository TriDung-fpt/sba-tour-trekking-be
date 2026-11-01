package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.List;

public record SaveItineraryRequest(@NotNull @Size(max = 60) List<@NotNull @Valid Day> days) {
    public record Day(@Positive int dayNumber, @NotBlank @Size(max = 250) String title,
                      @Size(max = 4000) String meals, @Size(max = 4000) String overnight,
                      @NotNull @Size(max = 100) List<@NotNull @Valid Activity> activities) {}
    public record Activity(LocalTime startTime, @NotBlank @Size(max = 250) String title,
                           @Size(max = 10000) String description, @Positive Long locationId) {}
}
