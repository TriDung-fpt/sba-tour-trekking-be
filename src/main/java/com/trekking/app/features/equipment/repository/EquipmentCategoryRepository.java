package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentCategoryRepository extends JpaRepository<EquipmentCategory, Long> {
    List<EquipmentCategory> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
