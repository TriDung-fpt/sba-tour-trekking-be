package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record ReorderTourMediaRequest(@NotNull List<@NotNull @Positive Long> mediaIds) {}
