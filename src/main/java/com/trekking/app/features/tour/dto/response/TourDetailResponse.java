package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record TourDetailResponse(TourSummary summary, String description, String requiredExperience,
                                 String includedServices, String excludedServices, TourCompanySummary company,
                                 List<TourGalleryMedia> gallery, List<TourItineraryActivity> itinerary, List<TourPublicSchedule> schedules,
                                 String routeFileUrl, TourPolicySummary cancellationPolicy,
                                 List<CancellationRefundRuleDto> cancellationRefundRules,
                                 List<TourPackingItem> packingList, TourEquipmentDetails equipment,
                                 BigDecimal averageTourRating, long reviewCount) {}
