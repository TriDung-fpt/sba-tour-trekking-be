package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentReservationItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentReservationItemRepository extends JpaRepository<EquipmentReservationItem, Long> {
    @EntityGraph(attributePaths = "equipment")
    List<EquipmentReservationItem> findAllByReservationIdOrderByIdAsc(long reservationId);
}
