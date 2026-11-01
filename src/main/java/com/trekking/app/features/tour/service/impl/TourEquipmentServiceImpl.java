package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.AssignTourEquipmentRequest;
import com.trekking.app.features.tour.dto.response.RentalItemResponse;
import com.trekking.app.features.tour.dto.response.RentalPackageResponse;
import com.trekking.app.features.tour.dto.response.RequiredItemResponse;
import com.trekking.app.features.tour.dto.response.TourEquipmentResponse;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.entity.RentalPackageTemplate;
import com.trekking.app.features.tour.entity.RentalPackageTemplateItem;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourEquipmentItem;
import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import com.trekking.app.features.tour.enums.TourStatus;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourEquipmentMapper;
import com.trekking.app.features.tour.repository.*;
import com.trekking.app.features.tour.service.TourEquipmentService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import com.trekking.app.features.tour.entity.PackingItem;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
        Tour tour = requireTour(tourId);
        return equipmentForTour(tour);
    }

    @Override
    public TourEquipmentResponse publicEquipment(long tourId) {
        Tour tour = requirePublishedTour(tourId);
        return equipmentForTour(tour);
    }

    @Override
    @Transactional
    public TourEquipmentResponse assignEquipment(long tourId, AssignTourEquipmentRequest request) {
        // Serialize changes to a tour's equipment configuration, including the empty case.
        Tour tour = tours.findLockedById(tourId).orElseThrow(() ->
                new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found"));

        long currentVersion = tour.getEquipmentConfigVersion() == null ? 0L : tour.getEquipmentConfigVersion();
        Long requestedVersion = request == null ? null : request.version();
        if (requestedVersion == null) {
            if (currentVersion > 0L) {
                throw new TourException(
                        HttpStatus.CONFLICT,
                        "EQUIPMENT_CONFIG_CONFLICT",
                        "Tour equipment configuration has already been initialized (version "
                                + currentVersion
                                + "). Reload the latest configuration and include its version when saving.");
            }
        } else if (!requestedVersion.equals(currentVersion)) {
            throw new TourException(
                    HttpStatus.CONFLICT,
                    "EQUIPMENT_CONFIG_CONFLICT",
                    "Tour equipment configuration was modified by another session (expected version "
                            + requestedVersion
                            + ", actual version "
                            + currentVersion
                            + "). Please reload and try again.");
        }

        List<Long> templateIds = resolvePackageTemplateIds(request);
        List<Long> individualIds = request == null || request.individualEquipmentIds() == null
                ? List.of()
                : request.individualEquipmentIds();

        if (templateIds.stream().anyMatch(id -> id == null || id <= 0)
                || individualIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "Equipment and rental package IDs must be positive");
        }
        if (templateIds.stream().distinct().count() != templateIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "A rental package can only be selected once");
        }
        if (individualIds.stream().distinct().count() != individualIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "An equipment item can only be selected once");
        }

        Map<Long, RentalPackageTemplate> foundTemplates = packageTemplates.findAllById(templateIds).stream()
                .collect(Collectors.toMap(RentalPackageTemplate::getId, Function.identity()));
        if (foundTemplates.size() != templateIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "One or more rental packages were not found");
        }

        Map<Long, Equipment> foundEquipment = equipment.findAllById(individualIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        if (foundEquipment.size() != individualIds.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "One or more equipment items were not found");
        }
        if (foundEquipment.values().stream().anyMatch(item -> item.getIndividualRentalPriceVnd() == null)) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION",
                    "One or more selected items are not available for individual rental");
        }

        // Remove existing standalone individual items for this tour before re-inserting.
        items.deleteAllByTourIdAndPackageIdIsNull(tourId);

        // Reuse existing active TourEquipmentPackage rows when possible; archive (active=false) instead of deleting if referenced by reservations.
        List<TourEquipmentPackage> existingPackages = packages.findAllByTourIdAndActiveTrueOrderByIdAsc(tourId);
        Set<Long> requestedTemplateSet = new LinkedHashSet<>(templateIds);
        Map<Long, TourEquipmentPackage> reusableByTemplateId = new LinkedHashMap<>();
        List<TourEquipmentPackage> obsoletePackages = new ArrayList<>();
        for (TourEquipmentPackage existing : existingPackages) {
            Long existingTemplateId = existing.getPackageTemplateId();
            if (existingTemplateId != null && requestedTemplateSet.contains(existingTemplateId)
                    && !reusableByTemplateId.containsKey(existingTemplateId)) {
                reusableByTemplateId.put(existingTemplateId, existing);
            } else {
                obsoletePackages.add(existing);
            }
        }
        for (TourEquipmentPackage obsolete : obsoletePackages) {
            if (packages.isReferencedByReservation(obsolete.getId())) {
                obsolete.setActive(false);
                obsolete.setNeedsReview(false);
                packages.save(obsolete);
            } else {
                items.deleteAllByPackageId(obsolete.getId());
                packages.delete(obsolete);
            }
        }
        items.flush();
        packages.flush();

        for (Long templateId : templateIds) {
            RentalPackageTemplate template = foundTemplates.get(templateId);
            List<RentalPackageTemplateItem> templateRows = packageTemplateItems
                    .findAllByPackageTemplateIdOrderByIdAsc(templateId);
            String summary = templateRows.stream()
                    .map(row -> row.getEquipment().getName() + " × " + row.getQuantityInPackage())
                    .collect(Collectors.joining(", "));
            String resolvedIncludedItems = summary.isBlank() ? template.getTitle() : summary;

            TourEquipmentPackage tourPackage = reusableByTemplateId.get(templateId);
            if (tourPackage != null && packages.isReferencedByReservation(tourPackage.getId())) {
                List<TourEquipmentItem> currentPkgItems = items.findAllByPackageIdOrderByIdAsc(tourPackage.getId());
                if (Boolean.FALSE.equals(tourPackage.getNeedsReview())
                        && matchesTemplateSnapshot(tourPackage, currentPkgItems, template, resolvedIncludedItems, templateRows)) {
                    // Snapshot and items match and are referenced by historical reservations; keep intact.
                    continue;
                }
                // Archive the historical snapshot so existing reservations keep their exact package & item rows.
                tourPackage.setActive(false);
                tourPackage.setNeedsReview(false);
                packages.saveAndFlush(tourPackage);
                tourPackage = null;
            }

            if (tourPackage == null) {
                tourPackage = new TourEquipmentPackage();
                tourPackage.setTourId(tourId);
                tourPackage.setPackageTemplateId(templateId);
            } else {
                items.deleteAllByPackageId(tourPackage.getId());
                items.flush();
            }
            tourPackage.setTitle(template.getTitle());
            tourPackage.setIncludedItems(resolvedIncludedItems);
            tourPackage.setRentalPriceVnd(template.getRentalPriceVnd());
            tourPackage.setActive(true);
            tourPackage.setNeedsReview(false);
            tourPackage = packages.saveAndFlush(tourPackage);

            Long packageId = tourPackage.getId();
            List<TourEquipmentItem> packageItemRows = new ArrayList<>();
            for (RentalPackageTemplateItem row : templateRows) {
                packageItemRows.add(toTourItem(tourId, packageId, row.getEquipment(), row.getQuantityInPackage(),
                        row.getIndividualRentalPriceVnd() != null ? row.getIndividualRentalPriceVnd()
                                : row.getEquipment().getIndividualRentalPriceVnd()));
            }
            items.saveAll(packageItemRows);
        }

        List<TourEquipmentItem> individualRows = new ArrayList<>();
        for (Long equipmentId : individualIds) {
            Equipment item = foundEquipment.get(equipmentId);
            individualRows.add(toTourItem(tourId, null, item, 1, item.getIndividualRentalPriceVnd()));
        }
        items.saveAll(individualRows);
        items.flush();

        tour.setEquipmentConfigVersion(currentVersion + 1L);
        tours.saveAndFlush(tour);

        return equipmentForTour(tour);
    }

    private static boolean matchesTemplateSnapshot(
            TourEquipmentPackage tourPackage,
            List<TourEquipmentItem> currentItems,
            RentalPackageTemplate template,
            String resolvedIncludedItems,
            List<RentalPackageTemplateItem> templateRows) {
        if (!Objects.equals(tourPackage.getTitle(), template.getTitle())
                || !Objects.equals(tourPackage.getIncludedItems(), resolvedIncludedItems)
                || !Objects.equals(tourPackage.getRentalPriceVnd(), template.getRentalPriceVnd())
                || currentItems.size() != templateRows.size()) {
            return false;
        }
        for (int i = 0; i < templateRows.size(); i++) {
            TourEquipmentItem actual = currentItems.get(i);
            RentalPackageTemplateItem expected = templateRows.get(i);
            Long expectedPrice = expected.getIndividualRentalPriceVnd() != null
                    ? expected.getIndividualRentalPriceVnd()
                    : expected.getEquipment().getIndividualRentalPriceVnd();
            if (!Objects.equals(actual.getEquipmentId(), expected.getEquipmentId())
                    || !Objects.equals(actual.getQuantityInPackage(), expected.getQuantityInPackage())
                    || !Objects.equals(actual.getIndividualRentalPriceVnd(), expectedPrice)) {
                return false;
            }
        }
        return true;
    }

    private static List<Long> resolvePackageTemplateIds(AssignTourEquipmentRequest request) {
        if (request == null) return List.of();
        if (request.packageTemplateIds() != null) {
            if (request.packageTemplateId() != null && !request.packageTemplateIds().contains(request.packageTemplateId())) {
                List<Long> combined = new ArrayList<>(request.packageTemplateIds());
                combined.add(request.packageTemplateId());
                return combined;
            }
            return request.packageTemplateIds();
        }
        if (request.packageTemplateId() != null) {
            return List.of(request.packageTemplateId());
        }
        return List.of();
    }

    private static TourEquipmentItem toTourItem(long tourId, Long packageId, Equipment equipment, int quantity, Long price) {
        TourEquipmentItem item = new TourEquipmentItem();
        item.setTourId(tourId);
        item.setPackageId(packageId);
        item.setEquipmentId(equipment.getId());
        item.setQuantityInPackage(quantity);
        item.setIndividualRentalPriceVnd(price);
        item.setEquipment(equipment);
        return item;
    }

    private TourEquipmentResponse equipmentForTour(Tour tour) {
        long tourId = tour.getId();
        long version = tour.getEquipmentConfigVersion() == null ? 0L : tour.getEquipmentConfigVersion();
        List<TourEquipmentPackage> tourPackages = packages.findAllByTourIdAndActiveTrueOrderByIdAsc(tourId);
        List<RentalPackageResponse> packageResponses = tourPackages.stream()
                .map(pkg -> {
                    List<RentalItemResponse> pkgItems = items.findAllByPackageIdOrderByIdAsc(pkg.getId()).stream()
                            .map(mapper::toResponse)
                            .toList();
                    return mapper.toResponse(pkg, pkgItems);
                })
                .toList();
        List<RentalItemResponse> individualResponses = items.findAllByTourIdAndPackageIdIsNullOrderByIdAsc(tourId).stream()
                .map(mapper::toResponse)
                .toList();
        return new TourEquipmentResponse(version, packageResponses, individualResponses);
    }

    private Tour requireTour(long tourId) {
        return tours.findById(tourId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found"));
    }

    private Tour requirePublishedTour(long tourId) {
        var tour = tours.findById(tourId)
                .orElseThrow(() -> new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found"));
        if (tour.getStatus() != TourStatus.PUBLISHED) {
            throw new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Tour was not found");
        }
        return tour;
    }
}
