package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record TourDetail(long id, long companyId, String title, String description, String status,
                             String difficulty, Integer durationDays, String requiredExperience,
                             String includedServices, String excludedServices, String routeFileUrl,
                             Long cancellationPolicyVersionId, String reviewNote, List<Long> locationIds,
                             List<TourManagementMedia> media, List<TourManagementActivity> itinerary, List<TourManagementPacking> packingList,
                             List<TourManagementEquipment> equipmentOptions, List<TourManagementSchedule> schedules) {}
