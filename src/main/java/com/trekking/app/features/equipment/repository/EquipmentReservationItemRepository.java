package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentReservationItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentReservationItemRepository extends JpaRepository<EquipmentReservationItem, Long> {
    List<EquipmentReservationItem> findAllByReservationIdOrderByIdAsc(long reservationId);
    boolean existsByEquipmentId(long equipmentId);
}
