package com.trekking.app.features.tour.dto.request;

import jakarta.validation.constraints.Size;

public record ParticipantCheckOutRequest(@Size(max = 1000) String note) {}
