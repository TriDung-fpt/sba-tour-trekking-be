package com.trekking.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.trekking.app.features.tour.enums.EquipmentStatus;
import com.trekking.app.features.tour.repository.EquipmentRepository;
import com.trekking.app.features.tour.service.EquipmentCatalogService;
import com.trekking.app.features.equipment.repository.EquipmentInventoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EquipmentLifecycleIntegrationTests extends AuthTestInfrastructure {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EquipmentRepository equipment;
    @Autowired private EquipmentCatalogService catalog;
    @Autowired private EquipmentInventoryRepository inventory;

    @Test
    void deletionPreservesInventoryAndExcludesCatalogItem() {
        long id = jdbc.queryForObject("insert into trekking.equipment(name) values (?) returning id", Long.class,
                "lifecycle-" + UUID.randomUUID());
        jdbc.update("insert into trekking.equipment_inventory(equipment_id, total_quantity) values (?, 3)", id);

        assertThat(inventory.findAllWithEquipmentOrdered()).anyMatch(item -> item.getEquipmentId() == id);

        catalog.deleteEquipment(id);

        assertThat(jdbc.queryForObject("select status from trekking.equipment where id = ?", String.class, id)).isEqualTo("deleted");
        assertThat(jdbc.queryForObject("select total_quantity from trekking.equipment_inventory where equipment_id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(equipment.findById(id)).isPresent().get().extracting("status").isEqualTo(EquipmentStatus.DELETED);
        assertThat(equipment.findAllByStatusNotOrderByNameAscIdAsc(EquipmentStatus.DELETED))
                .noneMatch(item -> item.getId() == id);
    }
}
