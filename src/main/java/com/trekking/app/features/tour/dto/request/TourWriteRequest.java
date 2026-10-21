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

public record TourWriteRequest(
        @NotBlank @Size(max = 250) String title,
        @Size(max = 20000) String description,
        @Pattern(regexp = "easy|moderate|hard|expert") String difficulty,
        @Positive Integer durationDays,
        @Size(max = 5000) String requiredExperience,
        @Size(max = 10000) String includedServices,
        @Size(max = 10000) String excludedServices,
        @Size(max = 2048) @Pattern(regexp = "(?i)(https://[^\\s?#]+\\.(geojson|gpx)(\\?[^\\s#]*)?|/[^\\s?#]+\\.(geojson|gpx)(\\?[^\\s#]*)?)") String routeFileUrl,
        @Positive Long cancellationPolicyVersionId,
        @Valid @Size(max = 8) List<CancellationRefundRuleRequest> cancellationRefundRules,
        @Size(max = 50) List<@Positive Long> locationIds,
        @Valid @Size(max = 100) List<MediaItem> media,
        @Valid @Size(max = 200) List<ActivityItem> itinerary,
        @Valid @Size(max = 100) List<PackingItem> packingList) {}
