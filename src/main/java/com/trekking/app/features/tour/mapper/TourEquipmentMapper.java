package com.trekking.app.features.tour.mapper;

import com.trekking.app.features.tour.dto.response.*;
import com.trekking.app.features.tour.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TourEquipmentMapper {
    RequiredItemResponse toResponse(PackingItem item);
    RentalPackageResponse toResponse(TourEquipmentPackage equipmentPackage);
    EquipmentCatalogResponse toResponse(Equipment equipment);
    @Mapping(target = "name", source = "equipment.name")
    @Mapping(target = "description", source = "equipment.description")
    RentalItemResponse toResponse(TourEquipmentItem item);
}
