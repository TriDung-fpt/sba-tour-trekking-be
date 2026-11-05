package com.trekking.app.features.tour.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record AssignScheduleGuidesRequest(@NotNull List<@Valid GuideAssignment> guides) {
    public record GuideAssignment(@NotNull @Positive Long guideId, @NotNull Boolean isLead) {}
}
