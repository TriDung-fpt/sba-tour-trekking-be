package com.trekking.app.features.tour.mapper;

import com.trekking.app.features.tour.dto.request.ScheduleRequest;
import com.trekking.app.features.tour.dto.request.TourRequest;
import com.trekking.app.features.tour.dto.response.ScheduleResponse;
import com.trekking.app.features.tour.dto.response.TourResponse;
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
    void updateTour(TourRequest request, @MappingTarget Tour tour);

    @Mapping(target = "tourId", ignore = true)
    @Mapping(target = "heldSeats", ignore = true)
    @Mapping(target = "confirmedSeats", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "actualCompletedAt", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "lockVersion", ignore = true)
    void updateSchedule(ScheduleRequest request, @MappingTarget TourSchedule schedule);

    TourResponse toResponse(Tour tour);

    ScheduleResponse toResponse(TourSchedule schedule);
}
