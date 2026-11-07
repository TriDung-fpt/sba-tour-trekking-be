package com.trekking.app.features.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment) {
}
