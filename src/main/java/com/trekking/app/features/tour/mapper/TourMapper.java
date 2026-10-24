package com.trekking.app.features.tour.mapper;

import com.trekking.app.features.tour.dto.request.CreateScheduleRequest;
import com.trekking.app.features.tour.dto.request.CreateTourRequest;
import com.trekking.app.features.tour.dto.request.UpdateScheduleRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleDetailResponse;
import com.trekking.app.features.tour.dto.response.ScheduleSummaryResponse;
import com.trekking.app.features.tour.dto.response.TourDetailResponse;
import com.trekking.app.features.tour.dto.response.TourMediaResponse;
import com.trekking.app.features.tour.dto.response.TourSummaryResponse;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourSchedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TourMapper {
    @Mapping(target = "title", expression = "java(request.title().trim())")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancellationPolicyVersionId", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    Tour toTour(CreateTourRequest request);

    @Mapping(target = "title", expression = "java(request.title().trim())")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancellationPolicyVersionId", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    void updateTour(UpdateTourRequest request, @MappingTarget Tour tour);

    @Mapping(target = "tourId", ignore = true)
    @Mapping(target = "heldSeats", ignore = true)
    @Mapping(target = "confirmedSeats", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "actualCompletedAt", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "lockVersion", ignore = true)
    TourSchedule toSchedule(CreateScheduleRequest request);

    @Mapping(target = "tourId", ignore = true)
    @Mapping(target = "heldSeats", ignore = true)
    @Mapping(target = "confirmedSeats", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "actualCompletedAt", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "lockVersion", ignore = true)
    void updateSchedule(UpdateScheduleRequest request, @MappingTarget TourSchedule schedule);

    @Mapping(target = "media", source = "media")
    TourDetailResponse toDetailResponse(Tour tour, java.util.List<TourMediaResponse> media);

    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    TourSummaryResponse toSummaryResponse(Tour tour, String coverImageUrl);

    TourMediaResponse toResponse(com.trekking.app.features.tour.entity.TourMedia media);

    ScheduleDetailResponse toDetailResponse(TourSchedule schedule);

    ScheduleSummaryResponse toSummaryResponse(TourSchedule schedule);
}
