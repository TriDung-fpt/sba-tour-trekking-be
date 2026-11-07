package com.trekking.app.features.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.trekking.app.core.security.authorization.AuthorizationService;
import com.trekking.app.features.equipment.entity.EquipmentInventory;
import com.trekking.app.features.equipment.entity.EquipmentCategory;
import com.trekking.app.features.equipment.dto.request.EquipmentRequests;
import com.trekking.app.features.equipment.repository.EquipmentCategoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.repository.EquipmentReservationRepository;
import com.trekking.app.features.equipment.service.impl.EquipmentServiceImpl;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.enums.EquipmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EquipmentStatusTests {
    @Test
    void updateInventoryUsesTheSameCommittedQuantityForValidationAndResponse() {
        var inventory = mock(EquipmentInventoryRepository.class);
        var categories = mock(EquipmentCategoryRepository.class);
        var reservations = mock(EquipmentReservationRepository.class);
        var entities = mock(EntityManager.class);
        var item = new Equipment();
        item.setName("Tent");
        var category = new EquipmentCategory();
        ReflectionTestUtils.setField(category, "id", 2L);
        category.setName("Camping");
        var stock = new EquipmentInventory();
        stock.setEquipmentId(1L);
        when(entities.find(Equipment.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(item);
        when(categories.findById(2L)).thenReturn(Optional.of(category));
        when(inventory.findByEquipmentIdForUpdate(1)).thenReturn(Optional.of(stock));
        when(reservations.committedQuantityOverall(eq(1L), any())).thenReturn(3L);
        var service = new EquipmentServiceImpl(inventory, categories, reservations,
                mock(AuthorizationService.class), entities, Clock.systemUTC());

        var result = service.updateInventory(1, new EquipmentRequests.SaveInventory(2L, 10, 2, true));

        assertThat(result.availableQuantity()).isEqualTo(5);
        assertThat(result.active()).isTrue();
        verify(reservations).committedQuantityOverall(eq(1L), any());
    }

    @Test
    void maintenanceEquipmentHasNoAvailableStock() {
        var inventory = mock(EquipmentInventoryRepository.class);
        var item = new Equipment();
        item.setName("Tent");
        item.setStatus(EquipmentStatus.MAINTENANCE);
        var stock = new EquipmentInventory();
        stock.setEquipmentId(1L);
        stock.setEquipment(item);
        stock.setTotalQuantity(10);
        stock.setMaintenanceQuantity(0);
        stock.setActive(true);
        when(inventory.findAllWithEquipmentOrdered()).thenReturn(List.of(stock));
        var service = new EquipmentServiceImpl(inventory, mock(EquipmentCategoryRepository.class),
                mock(EquipmentReservationRepository.class), mock(AuthorizationService.class),
                mock(EntityManager.class), Clock.systemUTC());

        var result = service.listEquipment().getFirst();

        assertThat(result.active()).isFalse();
        assertThat(result.availableQuantity()).isZero();
        assertThat(result.totalQuantity()).isEqualTo(10);
    }
}
