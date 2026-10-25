package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.Equipment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findAllByOrderByNameAscIdAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
