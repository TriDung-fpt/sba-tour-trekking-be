package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourEquipmentMapper;
import com.trekking.app.features.tour.repository.*;
import com.trekking.app.features.tour.service.TourEquipmentService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TourEquipmentServiceImpl implements TourEquipmentService {
    private final TourRepository tours;
    private final PackingItemRepository requiredItems;
    private final TourEquipmentPackageRepository packages;
    private final TourEquipmentItemRepository items;
    private final TourEquipmentMapper mapper;

    public TourEquipmentServiceImpl(TourRepository tours, PackingItemRepository requiredItems,
            TourEquipmentPackageRepository packages, TourEquipmentItemRepository items, TourEquipmentMapper mapper) {
        this.tours = tours;
        this.requiredItems = requiredItems;
        this.packages = packages;
        this.items = items;
        this.mapper = mapper;
    }

    @Override
    public List<RequiredItemResponse> requiredItems(long tourId) {
        requireTour(tourId);
        return requiredItems.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public TourEquipmentResponse equipment(long tourId) {
        requireTour(tourId);
        return packages.findByTourId(tourId).map(equipmentPackage -> new TourEquipmentResponse(
                mapper.toResponse(equipmentPackage), items.findAllByPackageIdOrderByIdAsc(equipmentPackage.getId())
                        .stream().map(mapper::toResponse).toList()))
                .orElseGet(() -> new TourEquipmentResponse(null, List.of()));
    }

    private void requireTour(long tourId) {
        if (!tours.existsById(tourId)) throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
    }
}
