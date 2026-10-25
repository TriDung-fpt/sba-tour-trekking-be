package com.trekking.app.features.tour.mapper;

import com.trekking.app.features.tour.dto.request.CreateActivityRequest;
import com.trekking.app.features.tour.dto.request.UpdateActivityRequest;
import com.trekking.app.features.tour.dto.response.ActivityResponse;
import com.trekking.app.features.tour.entity.TourActivity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TourActivityMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tourId", source = "tourId")
    @Mapping(target = "title", expression = "java(request.title().trim())")
    @Mapping(target = "tour", ignore = true)
    @Mapping(target = "location", ignore = true)
    TourActivity toEntity(CreateActivityRequest request, long tourId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tourId", ignore = true)
    @Mapping(target = "title", expression = "java(request.title().trim())")
    @Mapping(target = "tour", ignore = true)
    @Mapping(target = "location", ignore = true)
    void update(UpdateActivityRequest request, @MappingTarget TourActivity activity);

    ActivityResponse toResponse(TourActivity activity);
}
