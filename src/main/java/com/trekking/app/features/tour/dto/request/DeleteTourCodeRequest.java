package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteTourCodeRequest(@NotBlank @Size(max = 1000) String reason) {}
