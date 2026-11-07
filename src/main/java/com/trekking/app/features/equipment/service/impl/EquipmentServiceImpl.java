package com.trekking.app.features.equipment.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.core.security.authorization.Permission;
import com.trekking.app.features.equipment.dto.request.EquipmentRequests;
import com.trekking.app.features.equipment.dto.response.EquipmentResponses;
import com.trekking.app.features.equipment.entity.EquipmentCategory;
import com.trekking.app.features.equipment.entity.EquipmentInventory;
import com.trekking.app.features.equipment.repository.EquipmentCategoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.equipment.service.EquipmentService;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.enums.EquipmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EquipmentServiceImpl implements EquipmentService {
    private final EquipmentInventoryRepository inventory;
    private final EquipmentCategoryRepository categories;
    private final EquipmentReservationRepository reservations;
    private final AuthorizationService authorization;
    private final EntityManager entities;
    private final Clock clock;

    public EquipmentServiceImpl(EquipmentInventoryRepository inventory, EquipmentCategoryRepository categories,
            EquipmentReservationRepository reservations, AuthorizationService authorization,
            EntityManager entities, Clock clock) {
        this.inventory = inventory;
        this.categories = categories;
        this.reservations = reservations;
        this.authorization = authorization;
        this.entities = entities;
        this.clock = clock;
    }

    @Override
    public List<EquipmentResponses.EquipmentDetails> listEquipment() {
        authorize();
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inventory.findAllWithEquipmentOrdered().stream()
                .map(item -> details(item, reservations.committedQuantityOverall(item.getEquipmentId(), now)))
                .toList();
    }

    @Override
    @Transactional
    public EquipmentResponses.EquipmentDetails updateInventory(long equipmentId, EquipmentRequests.SaveInventory request) {
        authorize();
        Equipment catalogItem = entities.find(Equipment.class, equipmentId, LockModeType.PESSIMISTIC_WRITE);
        if (catalogItem == null) throw notFound();
        if (catalogItem.getStatus() == EquipmentStatus.DELETED) throw conflict("EQUIPMENT_INACTIVE", "Deleted equipment cannot be updated through inventory");
        EquipmentCategory category = categories.findById(request.categoryId()).orElseThrow(EquipmentServiceImpl::notFound);
        if (!Boolean.TRUE.equals(category.getActive())) throw conflict("EQUIPMENT_CATEGORY_INACTIVE", "Inactive category cannot be assigned");
        if (request.maintenanceQuantity() > request.totalQuantity()) {
            throw conflict("INVALID_MAINTENANCE_QUANTITY", "Maintenance quantity cannot exceed total quantity");
        }

        var existingInventory = inventory.findByEquipmentIdForUpdate(equipmentId);
        boolean isNewInventory = existingInventory.isEmpty();
        EquipmentInventory item = existingInventory.orElseGet(() -> {
            EquipmentInventory created = new EquipmentInventory();
            created.setEquipmentId(equipmentId);
            return created;
        });
        long committed = reservations.committedQuantityOverall(equipmentId, OffsetDateTime.now(clock));
        if ((long) request.totalQuantity() < (long) request.maintenanceQuantity() + committed) {
            throw conflict("EQUIPMENT_QUANTITY_BELOW_COMMITTED", "Total stock cannot be lower than maintenance and committed quantities");
        }
        item.setCategoryId(category.getId());
        item.setEquipment(catalogItem);
        item.setCategory(category);
        item.setTotalQuantity(request.totalQuantity());
        item.setMaintenanceQuantity(request.maintenanceQuantity());
        item.setActive(request.active());
        catalogItem.setStatus(Boolean.TRUE.equals(request.active()) ? EquipmentStatus.ACTIVE : EquipmentStatus.MAINTENANCE);
        if (isNewInventory) entities.persist(item);
        return details(item, committed);
    }

    @Override
    public List<EquipmentResponses.Category> listCategories() {
        authorize();
        return categories.findAllByOrderByNameAsc().stream().map(EquipmentServiceImpl::category).toList();
    }

    @Override
    @Transactional
    public EquipmentResponses.Category createCategory(EquipmentRequests.SaveCategory request) {
        authorize();
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) throw conflict("EQUIPMENT_CATEGORY_EXISTS", "Category name already exists");
        EquipmentCategory category = new EquipmentCategory();
        category.setName(name);
        category.setDescription(clean(request.description()));
        category.setActive(request.active() == null || request.active());
        return category(categories.save(category));
    }

    @Override
    @Transactional
    public EquipmentResponses.Category updateCategory(long categoryId, EquipmentRequests.SaveCategory request) {
        authorize();
        EquipmentCategory category = categories.findById(categoryId).orElseThrow(EquipmentServiceImpl::notFound);
        String name = request.name().trim();
        if (!category.getName().equalsIgnoreCase(name) && categories.existsByNameIgnoreCase(name)) {
            throw conflict("EQUIPMENT_CATEGORY_EXISTS", "Category name already exists");
        }
        category.setName(name);
        category.setDescription(clean(request.description()));
        category.setActive(request.active() == null || request.active());
        return category(categories.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(long categoryId) {
        authorize();
        EquipmentCategory category = categories.findById(categoryId).orElseThrow(EquipmentServiceImpl::notFound);
        if (inventory.existsByCategoryId(categoryId)) throw conflict("EQUIPMENT_CATEGORY_IN_USE", "Category is assigned to equipment");
        categories.delete(category);
    }

    private static EquipmentResponses.EquipmentDetails details(EquipmentInventory item, long committed) {
        boolean active = item.getActive() && item.getEquipment().getStatus() == EquipmentStatus.ACTIVE;
        int available = active ? Math.max(0, item.getTotalQuantity() - item.getMaintenanceQuantity() - Math.toIntExact(committed)) : 0;
        return new EquipmentResponses.EquipmentDetails(item.getEquipmentId(), item.getEquipment().getName(),
                item.getEquipment().getDescription(), item.getCategoryId(),
                item.getCategory() == null ? null : item.getCategory().getName(), item.getTotalQuantity(),
                item.getMaintenanceQuantity(), available, active);
    }

    private void authorize() { authorization.require(Permission.TOUR_MANAGE); }

    private static EquipmentResponses.Category category(EquipmentCategory value) {
        return new EquipmentResponses.Category(value.getId(), value.getName(), value.getDescription(), value.getActive());
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static AuthException notFound() {
        return new AuthException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private static AuthException conflict(String code, String message) {
        return new AuthException(HttpStatus.CONFLICT, code, message);
    }
}
