package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.dto.request.AssignTourEquipmentRequest;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.entity.RentalPackageTemplate;
import com.trekking.app.features.tour.entity.RentalPackageTemplateItem;
import com.trekking.app.features.tour.entity.TourEquipmentItem;
import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourEquipmentMapper;
import com.trekking.app.features.tour.repository.*;
import com.trekking.app.features.tour.service.TourEquipmentService;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final EquipmentRepository equipment;
    private final RentalPackageTemplateRepository packageTemplates;
    private final RentalPackageTemplateItemRepository packageTemplateItems;
    private final TourEquipmentMapper mapper;

    public TourEquipmentServiceImpl(TourRepository tours, PackingItemRepository requiredItems,
            TourEquipmentPackageRepository packages, TourEquipmentItemRepository items, EquipmentRepository equipment,
            RentalPackageTemplateRepository packageTemplates, RentalPackageTemplateItemRepository packageTemplateItems,
            TourEquipmentMapper mapper) {
        this.tours = tours;
        this.requiredItems = requiredItems;
        this.packages = packages;
        this.items = items;
        this.equipment = equipment;
        this.packageTemplates = packageTemplates;
        this.packageTemplateItems = packageTemplateItems;
        this.mapper = mapper;
    }

    @Override
    public List<RequiredItemResponse> requiredItems(long tourId) {
        requireTour(tourId);
        return requiredItems.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public List<RequiredItemResponse> publicRequiredItems(long tourId) {
        requirePublishedTour(tourId);
        return requiredItems.findAllByTourIdOrderByDisplayOrderAscIdAsc(tourId).stream().map(mapper::toResponse).toList();
    }

    @Override
    public TourEquipmentResponse equipment(long tourId) {
        requireTour(tourId);
        return equipmentForTour(tourId);
    }

    @Override
    public TourEquipmentResponse publicEquipment(long tourId) {
        requirePublishedTour(tourId);
        return equipmentForTour(tourId);
    }

    @Override
    @Transactional
    public TourEquipmentResponse assignEquipment(long tourId, AssignTourEquipmentRequest request) {
        requireTour(tourId);
        List<Long> individualIds = request.individualEquipmentIds() == null ? List.of() : request.individualEquipmentIds();
        if (individualIds.stream().distinct().count() != individualIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "An equipment item can only be selected once");
        }

        RentalPackageTemplate template = request.packageTemplateId() == null ? null : packageTemplates.findById(request.packageTemplateId())
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Rental package was not found"));
        List<RentalPackageTemplateItem> templateRows = template == null ? List.of()
                : packageTemplateItems.findAllByPackageTemplateIdOrderByIdAsc(template.getId());
        Set<Long> packagedEquipmentIds = templateRows.stream().map(RentalPackageTemplateItem::getEquipmentId).collect(Collectors.toSet());
        if (individualIds.stream().anyMatch(packagedEquipmentIds::contains)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "Equipment already included in the selected package cannot be added again");
        }

        List<Equipment> individualEquipment = equipment.findAllById(individualIds);
        if (individualEquipment.size() != individualIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "One or more equipment items were not found");
        }
        if (individualEquipment.stream().anyMatch(item -> item.getIndividualRentalPriceVnd() == null)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "One or more selected items are not available for individual rental");
        }

        packages.findByTourId(tourId).ifPresent(existing -> {
            items.deleteAllByPackageId(existing.getId());
            packages.delete(existing);
            packages.flush();
        });

        if (template == null && individualEquipment.isEmpty()) return new TourEquipmentResponse(null, List.of());

        TourEquipmentPackage tourPackage = new TourEquipmentPackage();
        tourPackage.setTourId(tourId);
        tourPackage.setPackageTemplateId(template == null ? null : template.getId());
        tourPackage.setTitle(template == null ? "Thuê dụng cụ lẻ" : template.getTitle());
        tourPackage.setIncludedItems(templateRows.stream()
                .map(row -> row.getEquipment().getName() + " × " + row.getQuantityInPackage())
                .collect(Collectors.joining(", ")));
        tourPackage.setSharedItems(template == null ? null : template.getSharedItems());
        tourPackage.setPersonalItemsToBring(template == null ? null : template.getPersonalItemsToBring());
        tourPackage.setRentalPriceVnd(template == null ? null : template.getRentalPriceVnd());
        tourPackage = packages.saveAndFlush(tourPackage);

        Long packageId = tourPackage.getId();
        List<TourEquipmentItem> tourRows = new ArrayList<>();
        for (RentalPackageTemplateItem row : templateRows) {
            tourRows.add(toTourItem(packageId, row.getEquipment(), row.getQuantityInPackage(),
                    row.getIndividualRentalPriceVnd() != null ? row.getIndividualRentalPriceVnd()
                            : row.getEquipment().getIndividualRentalPriceVnd()));
        }
        for (Equipment item : individualEquipment) {
            tourRows.add(toTourItem(packageId, item, 1, item.getIndividualRentalPriceVnd()));
        }
        List<TourEquipmentItem> savedItems = items.saveAll(tourRows);
        return new TourEquipmentResponse(mapper.toResponse(tourPackage),
                savedItems.stream().map(mapper::toResponse).toList());
    }

    private static TourEquipmentItem toTourItem(long packageId, Equipment equipment, int quantity, Long price) {
        TourEquipmentItem item = new TourEquipmentItem();
        item.setPackageId(packageId);
        item.setEquipmentId(equipment.getId());
        item.setQuantityInPackage(quantity);
        item.setIndividualRentalPriceVnd(price);
        item.setEquipment(equipment);
        return item;
    }

    private TourEquipmentResponse equipmentForTour(long tourId) {
        return packages.findByTourId(tourId).map(equipmentPackage -> new TourEquipmentResponse(
                mapper.toResponse(equipmentPackage), items.findAllByPackageIdOrderByIdAsc(equipmentPackage.getId())
                        .stream().map(mapper::toResponse).toList()))
                .orElseGet(() -> new TourEquipmentResponse(null, List.of()));
    }

    private void requireTour(long tourId) {
        if (!tours.existsById(tourId)) throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
    }

    private void requirePublishedTour(long tourId) {
        var tour = tours.findById(tourId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found"));
        if (tour.getStatus() != TourStatus.PUBLISHED) {
            throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
        }
    }
}
