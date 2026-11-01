package com.trekking.app.features.tour.dto.request;
import jakarta.validation.constraints.*;
import java.util.List;
public record SaveTourTermsRequest(@NotNull @Size(max = 50) List<@NotNull @Positive Long> versionIds,
                                   @Positive Long cancellationPolicyVersionId,
                                   Boolean removeCancellationPolicy) {}
