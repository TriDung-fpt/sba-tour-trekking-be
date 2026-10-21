package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record MediaItem(@NotBlank @Size(max = 2048) @Pattern(regexp = "^(https?://|/)[^\\s]+$") String url,
                            @NotBlank String mediaType, @Size(max = 500) String caption,
                            @Min(0) @Max(1000) int displayOrder, boolean cover) {}
