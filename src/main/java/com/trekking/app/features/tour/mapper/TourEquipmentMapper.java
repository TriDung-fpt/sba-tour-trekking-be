package com.trekking.app.features.tour.mapper;

import com.trekking.app.features.tour.dto.response.*;
import com.trekking.app.features.tour.entity.*;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TourEquipmentMapper {
    RequiredItemResponse toResponse(PackingItem item);

    @Mapping(target = "items", source = "items")
    @Mapping(target = "needsReview", expression = "java(Boolean.TRUE.equals(equipmentPackage.getNeedsReview()))")
    RentalPackageResponse toResponse(TourEquipmentPackage equipmentPackage, List<RentalItemResponse> items);

    EquipmentCatalogResponse toResponse(Equipment equipment);

    @Mapping(target = "name", source = "equipment.name")
    @Mapping(target = "description", source = "equipment.description")
    RentalItemResponse toResponse(TourEquipmentItem item);
}
