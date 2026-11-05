package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentHandoverRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentHandoverRepository extends JpaRepository<EquipmentHandoverRecord, Long> {
    List<EquipmentHandoverRecord> findAllByReservationIdOrderByOccurredAtAsc(long reservationId);
}
