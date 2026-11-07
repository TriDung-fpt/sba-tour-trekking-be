package com.trekking.app.features.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.trekking.app.features.storage.ImageStorage;
import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.entity.Tour;
import com.trekking.app.features.tour.entity.TourEquipmentItem;
import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import com.trekking.app.features.tour.entity.RentalPackageTemplate;
import com.trekking.app.features.tour.enums.EquipmentStatus;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import com.trekking.app.features.equipment.entity.EquipmentInventory;
import com.trekking.app.features.tour.dto.request.SaveEquipmentRequest;
import com.trekking.app.features.tour.dto.request.SaveRentalPackageRequest;
import com.trekking.app.features.tour.dto.response.RentalPackageResponse;
import com.trekking.app.features.tour.mapper.TourEquipmentMapper;
import com.trekking.app.features.tour.repository.EquipmentRepository;
import com.trekking.app.features.tour.repository.RentalPackageTemplateItemRepository;
import com.trekking.app.features.tour.repository.RentalPackageTemplateRepository;
import com.trekking.app.features.tour.repository.TourEquipmentPackageRepository;
import com.trekking.app.features.tour.repository.TourEquipmentItemRepository;
import com.trekking.app.features.tour.repository.TourRepository;
import com.trekking.app.features.tour.repository.PackingItemRepository;
import com.trekking.app.features.tour.service.impl.EquipmentCatalogServiceImpl;
import com.trekking.app.features.tour.service.impl.TourEquipmentServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

class EquipmentSoftDeleteTests {
    @Test
    void createPackageUsesTheValidatedEquipmentWithoutLoadingItAgain() {
        var equipment = mock(EquipmentRepository.class);
        var templates = mock(RentalPackageTemplateRepository.class);
        var packageItems = mock(RentalPackageTemplateItemRepository.class);
        var item = new Equipment();
        item.setId(15L);
        item.setName("Tent");
        when(equipment.findAllById(List.of(15L))).thenReturn(List.of(item));
        when(templates.save(any())).thenAnswer(invocation -> {
            RentalPackageTemplate template = invocation.getArgument(0);
            ReflectionTestUtils.setField(template, "id", 2L);
            return template;
        });
        var service = new EquipmentCatalogServiceImpl(equipment, mock(EquipmentInventoryRepository.class),
                templates, packageItems, mock(TourEquipmentPackageRepository.class),
                mock(TourEquipmentMapper.class), mock(ImageStorage.class), mock(PlatformTransactionManager.class));

        service.createPackage(new SaveRentalPackageRequest("Package", 100L,
                List.of(new SaveRentalPackageRequest.PackageItem(15L, 1, 10L))));

        verify(equipment).findAllById(List.of(15L));
        verify(packageItems).saveAll(anyList());
    }

    @Test
    void tourReadsEachAvailablePackageItemsOnce() {
        var tours = mock(TourRepository.class);
        var packages = mock(TourEquipmentPackageRepository.class);
        var items = mock(TourEquipmentItemRepository.class);
        var mapper = mock(TourEquipmentMapper.class);
        var tour = new Tour();
        ReflectionTestUtils.setField(tour, "id", 1L);
        var pack = new TourEquipmentPackage();
        ReflectionTestUtils.setField(pack, "id", 2L);
        when(tours.findById(1L)).thenReturn(Optional.of(tour));
        when(packages.findAllByTourIdAndActiveTrueOrderByIdAsc(1)).thenReturn(List.of(pack));
        when(items.findAllByPackageIdOrderByIdAsc(2)).thenReturn(List.of());
        when(mapper.toResponse(eq(pack), anyList()))
                .thenReturn(new RentalPackageResponse(2L, null, "Package", "", 0L, List.of()));
        var service = new TourEquipmentServiceImpl(tours, mock(PackingItemRepository.class), packages, items,
                mock(EquipmentRepository.class), mock(RentalPackageTemplateRepository.class),
                mock(RentalPackageTemplateItemRepository.class), mapper);

        assertThat(service.equipment(1).packages()).hasSize(1);
        verify(items).findAllByPackageIdOrderByIdAsc(2);
    }

    @Test
    void maintenanceAndActiveStatusKeepInventoryInSync() {
        var equipment = mock(EquipmentRepository.class);
        var inventory = mock(EquipmentInventoryRepository.class);
        var item = new Equipment();
        item.setId(15L);
        item.setName("Tent");
        var stock = new EquipmentInventory();
        stock.setActive(true);
        when(equipment.findForUpdate(15)).thenReturn(Optional.of(item));
        when(inventory.findByEquipmentIdForUpdate(15)).thenReturn(Optional.of(stock));
        when(equipment.save(item)).thenReturn(item);
        var service = new EquipmentCatalogServiceImpl(equipment, inventory,
                mock(RentalPackageTemplateRepository.class), mock(RentalPackageTemplateItemRepository.class),
                mock(TourEquipmentPackageRepository.class), mock(TourEquipmentMapper.class),
                mock(ImageStorage.class), mock(PlatformTransactionManager.class));

        service.updateEquipment(15, new SaveEquipmentRequest("Tent", null, null, EquipmentStatus.MAINTENANCE));
        assertThat(item.getStatus()).isEqualTo(EquipmentStatus.MAINTENANCE);
        assertThat(stock.getActive()).isFalse();

        service.updateEquipment(15, new SaveEquipmentRequest("Tent", null, null, EquipmentStatus.ACTIVE));
        assertThat(item.getStatus()).isEqualTo(EquipmentStatus.ACTIVE);
        assertThat(stock.getActive()).isTrue();
    }

    @Test
    void deleteKeepsEquipmentAndImageForHistoricalRecords() {
        var equipment = mock(EquipmentRepository.class);
        var storage = mock(ImageStorage.class);
        var item = new Equipment();
        item.setId(15L);
        item.setName("Tent");
        item.setStorageKey("equipment/tent");
        when(equipment.findForUpdate(15)).thenReturn(Optional.of(item));
        var service = new EquipmentCatalogServiceImpl(equipment, mock(EquipmentInventoryRepository.class),
                mock(RentalPackageTemplateRepository.class), mock(RentalPackageTemplateItemRepository.class),
                mock(TourEquipmentPackageRepository.class),
                mock(TourEquipmentMapper.class), storage, mock(PlatformTransactionManager.class));

        service.deleteEquipment(15);
        service.deleteEquipment(15);

        assertThat(item.getStatus()).isEqualTo(EquipmentStatus.DELETED);
        verify(equipment, times(2)).saveAndFlush(item);
        assertThat(item.getName()).isEqualTo("Tent");
        assertThat(item.getStorageKey()).isEqualTo("equipment/tent");
        verify(equipment, never()).delete(any());
        verifyNoInteractions(storage);
    }

    @Test
    void tourChoicesHideDeletedItemsAndPackagesContainingThem() {
        var tours = mock(TourRepository.class);
        var packages = mock(TourEquipmentPackageRepository.class);
        var items = mock(TourEquipmentItemRepository.class);
        var equipment = mock(EquipmentRepository.class);
        var tour = new Tour();
        ReflectionTestUtils.setField(tour, "id", 1L);
        var deleted = new Equipment();
        deleted.setId(15L);
        deleted.setStatus(EquipmentStatus.DELETED);
        var line = new TourEquipmentItem();
        line.setEquipment(deleted);
        line.setEquipmentId(15L);
        var pack = new TourEquipmentPackage();
        ReflectionTestUtils.setField(pack, "id", 2L);
        when(tours.findById(1L)).thenReturn(Optional.of(tour));
        when(packages.findAllByTourIdAndActiveTrueOrderByIdAsc(1)).thenReturn(List.of(pack));
        when(items.findAllByPackageIdOrderByIdAsc(2)).thenReturn(List.of(line));
        when(items.findAllByTourIdAndPackageIdIsNullOrderByIdAsc(1)).thenReturn(List.of(line));
        var service = new TourEquipmentServiceImpl(tours, mock(PackingItemRepository.class), packages, items,
                equipment, mock(RentalPackageTemplateRepository.class),
                mock(RentalPackageTemplateItemRepository.class), mock(TourEquipmentMapper.class));

        var choices = service.equipment(1);

        assertThat(choices.packages()).isEmpty();
        assertThat(choices.individualItems()).isEmpty();
        assertThat(line.getEquipment()).isSameAs(deleted);
    }
}
