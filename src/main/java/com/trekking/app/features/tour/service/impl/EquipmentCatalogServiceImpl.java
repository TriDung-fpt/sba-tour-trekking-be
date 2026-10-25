package com.trekking.app.features.tour.service.impl;

import com.trekking.app.features.tour.dto.request.SaveEquipmentRequest;
import com.trekking.app.features.tour.dto.request.SaveRentalPackageRequest;
import com.trekking.app.features.tour.dto.response.EquipmentCatalogResponse;
import com.trekking.app.features.tour.dto.response.RentalPackageTemplateItemResponse;
import com.trekking.app.features.tour.dto.response.RentalPackageTemplateResponse;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.entity.RentalPackageTemplate;
import com.trekking.app.features.tour.entity.RentalPackageTemplateItem;
import com.trekking.app.features.tour.exception.TourException;
import com.trekking.app.features.tour.mapper.TourEquipmentMapper;
import com.trekking.app.features.tour.repository.EquipmentRepository;
import com.trekking.app.features.tour.repository.RentalPackageTemplateItemRepository;
import com.trekking.app.features.tour.repository.RentalPackageTemplateRepository;
import com.trekking.app.features.tour.repository.TourEquipmentItemRepository;
import com.trekking.app.features.tour.repository.TourEquipmentPackageRepository;
import com.trekking.app.features.tour.service.EquipmentCatalogService;
import com.trekking.app.features.storage.ImageStorage;
import com.trekking.app.features.storage.ImageStorageException;
import com.trekking.app.features.storage.StoredImage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class EquipmentCatalogServiceImpl implements EquipmentCatalogService {
    private final EquipmentRepository equipment;
    private final RentalPackageTemplateRepository packageTemplates;
    private final RentalPackageTemplateItemRepository packageItems;
    private final TourEquipmentPackageRepository tourPackages;
    private final TourEquipmentItemRepository tourItems;
    private final TourEquipmentMapper mapper;
    private final ImageStorage storage;
    private final TransactionTemplate transactions;

    public EquipmentCatalogServiceImpl(EquipmentRepository equipment,
            RentalPackageTemplateRepository packageTemplates,
            RentalPackageTemplateItemRepository packageItems,
            TourEquipmentPackageRepository tourPackages,
            TourEquipmentItemRepository tourItems,
            TourEquipmentMapper mapper,
            ImageStorage storage,
            PlatformTransactionManager transactionManager) {
        this.equipment = equipment;
        this.packageTemplates = packageTemplates;
        this.packageItems = packageItems;
        this.tourPackages = tourPackages;
        this.tourItems = tourItems;
        this.mapper = mapper;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<EquipmentCatalogResponse> equipment() {
        return equipment.findAllByOrderByNameAscIdAsc().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public EquipmentCatalogResponse createEquipment(SaveEquipmentRequest request) {
        Equipment item = new Equipment();
        applyEquipment(item, request);
        return mapper.toResponse(equipment.save(item));
    }

    @Override
    @Transactional
    public EquipmentCatalogResponse updateEquipment(long equipmentId, SaveEquipmentRequest request) {
        Equipment item = equipment.findById(equipmentId).orElseThrow(() -> notFound("Equipment was not found"));
        applyEquipment(item, request);
        return mapper.toResponse(equipment.save(item));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EquipmentCatalogResponse uploadEquipmentImage(long equipmentId, MultipartFile file) {
        Equipment existing = equipment.findById(equipmentId).orElseThrow(() -> notFound("Equipment was not found"));
        String previousKey = existing.getStorageKey();
        var uploaded = uploadImage("trekking/equipment/" + equipmentId, file);
        EquipmentCatalogResponse response;
        try {
            response = transactions.execute(status -> {
                Equipment item = equipment.findById(equipmentId)
                        .orElseThrow(() -> notFound("Equipment was not found"));
                item.setImageUrl(uploaded.url());
                item.setStorageKey(uploaded.key());
                return mapper.toResponse(equipment.saveAndFlush(item));
            });
        } catch (RuntimeException ex) {
            storage.cleanup(uploaded.key());
            throw ex;
        }
        if (previousKey != null && !previousKey.equals(uploaded.key())) storage.cleanup(previousKey);
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteEquipmentImage(long equipmentId) {
        String key = transactions.execute(status -> {
            Equipment item = equipment.findById(equipmentId).orElseThrow(() -> notFound("Equipment was not found"));
            String currentKey = item.getStorageKey();
            item.setImageUrl(null);
            item.setStorageKey(null);
            equipment.saveAndFlush(item);
            return currentKey;
        });
        if (key != null) storage.cleanup(key);
    }

    @Override
    @Transactional
    public void deleteEquipment(long equipmentId) {
        Equipment item = equipment.findById(equipmentId).orElseThrow(() -> notFound("Equipment was not found"));
        if (packageItems.existsByEquipmentId(equipmentId) || tourItems.existsByEquipmentId(equipmentId)) {
            throw conflict("Equipment is used by a rental package or tour");
        }
        String key = item.getStorageKey();
        equipment.delete(item);
        equipment.flush();
        if (key != null) storage.cleanup(key);
    }

    @Override
    public List<RentalPackageTemplateResponse> packages() {
        return packageTemplates.findAllByOrderByTitleAscIdAsc().stream().map(this::toTemplateResponse).toList();
    }

    @Override
    @Transactional
    public RentalPackageTemplateResponse createPackage(SaveRentalPackageRequest request) {
        validatePackageItems(request);
        RentalPackageTemplate template = new RentalPackageTemplate();
        applyPackage(template, request);
        template = packageTemplates.save(template);
        savePackageItems(template.getId(), request.items());
        return toTemplateResponse(template);
    }

    @Override
    @Transactional
    public RentalPackageTemplateResponse updatePackage(long packageId, SaveRentalPackageRequest request) {
        validatePackageItems(request);
        RentalPackageTemplate template = packageTemplates.findById(packageId)
                .orElseThrow(() -> notFound("Rental package was not found"));
        applyPackage(template, request);
        template = packageTemplates.save(template);
        packageItems.deleteAllByPackageTemplateId(packageId);
        savePackageItems(packageId, request.items());
        return toTemplateResponse(template);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RentalPackageTemplateResponse uploadPackageImage(long packageId, MultipartFile file) {
        RentalPackageTemplate existing = packageTemplates.findById(packageId)
                .orElseThrow(() -> notFound("Rental package was not found"));
        String previousKey = existing.getStorageKey();
        var uploaded = uploadImage("trekking/rental-packages/" + packageId, file);
        RentalPackageTemplateResponse response;
        try {
            response = transactions.execute(status -> {
                RentalPackageTemplate template = packageTemplates.findById(packageId)
                        .orElseThrow(() -> notFound("Rental package was not found"));
                template.setImageUrl(uploaded.url());
                template.setStorageKey(uploaded.key());
                return toTemplateResponse(packageTemplates.saveAndFlush(template));
            });
        } catch (RuntimeException ex) {
            storage.cleanup(uploaded.key());
            throw ex;
        }
        if (previousKey != null && !previousKey.equals(uploaded.key())) storage.cleanup(previousKey);
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deletePackageImage(long packageId) {
        String key = transactions.execute(status -> {
            RentalPackageTemplate template = packageTemplates.findById(packageId)
                    .orElseThrow(() -> notFound("Rental package was not found"));
            String currentKey = template.getStorageKey();
            template.setImageUrl(null);
            template.setStorageKey(null);
            packageTemplates.saveAndFlush(template);
            return currentKey;
        });
        if (key != null) storage.cleanup(key);
    }

    @Override
    @Transactional
    public void deletePackage(long packageId) {
        RentalPackageTemplate template = packageTemplates.findById(packageId)
                .orElseThrow(() -> notFound("Rental package was not found"));
        if (tourPackages.existsByPackageTemplateId(packageId)) {
            throw conflict("Rental package is assigned to a tour");
        }
        String key = template.getStorageKey();
        packageTemplates.delete(template);
        packageTemplates.flush();
        if (key != null) storage.cleanup(key);
    }

    private void applyEquipment(Equipment item, SaveEquipmentRequest request) {
        String name = request.name().trim();
        boolean duplicate = item.getId() == null
                ? equipment.existsByNameIgnoreCase(name)
                : equipment.existsByNameIgnoreCaseAndIdNot(name, item.getId());
        if (duplicate) throw conflict("Equipment name already exists");
        item.setName(name);
        item.setDescription(trimToNull(request.description()));
        item.setIndividualRentalPriceVnd(request.individualRentalPriceVnd());
    }

    private void applyPackage(RentalPackageTemplate template, SaveRentalPackageRequest request) {
        template.setTitle(request.title().trim());
        template.setSharedItems(trimToNull(request.sharedItems()));
        template.setPersonalItemsToBring(trimToNull(request.personalItemsToBring()));
        template.setRentalPriceVnd(request.rentalPriceVnd());
    }

    private void validatePackageItems(SaveRentalPackageRequest request) {
        List<Long> ids = request.items().stream().map(SaveRentalPackageRequest.PackageItem::equipmentId).toList();
        if (ids.stream().distinct().count() != ids.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "A package cannot contain the same equipment more than once");
        }
        Map<Long, Equipment> found = equipment.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw new TourException(HttpStatus.BAD_REQUEST, "INVALID_EQUIPMENT_SELECTION", "One or more equipment items were not found");
        }
    }

    private void savePackageItems(long packageId, List<SaveRentalPackageRequest.PackageItem> requestedItems) {
        Map<Long, Equipment> found = equipment.findAllById(
                requestedItems.stream().map(SaveRentalPackageRequest.PackageItem::equipmentId).toList())
                .stream().collect(Collectors.toMap(Equipment::getId, Function.identity()));
        List<RentalPackageTemplateItem> rows = requestedItems.stream().map(input -> {
            RentalPackageTemplateItem item = new RentalPackageTemplateItem();
            item.setPackageTemplateId(packageId);
            item.setEquipmentId(input.equipmentId());
            item.setQuantityInPackage(input.quantityInPackage());
            item.setIndividualRentalPriceVnd(input.individualRentalPriceVnd());
            item.setEquipment(found.get(input.equipmentId()));
            return item;
        }).toList();
        packageItems.saveAll(rows);
    }

    private RentalPackageTemplateResponse toTemplateResponse(RentalPackageTemplate template) {
        List<RentalPackageTemplateItemResponse> rows = packageItems
                .findAllByPackageTemplateIdOrderByIdAsc(template.getId()).stream()
                .map(item -> new RentalPackageTemplateItemResponse(item.getEquipmentId(), item.getEquipment().getName(),
                        item.getEquipment().getDescription(), item.getQuantityInPackage(),
                        item.getIndividualRentalPriceVnd()))
                .toList();
        return new RentalPackageTemplateResponse(template.getId(), template.getTitle(), template.getSharedItems(),
                template.getPersonalItemsToBring(), template.getRentalPriceVnd(), template.getImageUrl(), rows);
    }

    private StoredImage uploadImage(String folder, MultipartFile file) {
        try {
            return storage.upload(file, folder);
        } catch (ImageStorageException ex) {
            throw mapStorageError(ex);
        }
    }

    private static TourException mapStorageError(ImageStorageException ex) {
        return switch (ex.getReason()) {
            case INVALID_IMAGE -> new TourException(HttpStatus.BAD_REQUEST, "INVALID_IMAGE",
                    "File must be a non-empty JPEG, PNG or WebP image", Map.of("file", "Choose a non-empty JPEG, PNG or WebP image"));
            case FILE_TOO_LARGE -> new TourException(HttpStatus.PAYLOAD_TOO_LARGE, "MAX_UPLOAD_SIZE",
                    "File must not exceed 5 MB", Map.of("file", "Must not exceed 5 MB"));
            case NOT_CONFIGURED -> new TourException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_NOT_CONFIGURED",
                    "Image storage credentials are not configured");
            case UNAVAILABLE -> new TourException(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_STORAGE_UNAVAILABLE",
                    "Image storage is temporarily unavailable");
        };
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static TourException notFound(String message) {
        return new TourException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    private static TourException conflict(String message) {
        return new TourException(HttpStatus.CONFLICT, "TOUR_DATA_CONFLICT", message);
    }
}
