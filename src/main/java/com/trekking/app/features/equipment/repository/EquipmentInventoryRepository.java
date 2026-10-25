package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.equipment.entity.EquipmentInventory;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentInventoryRepository extends JpaRepository<EquipmentInventory, Long> {
    @Query("select i from EquipmentInventory i join fetch i.equipment order by i.equipment.name")
    List<EquipmentInventory> findAllWithEquipmentOrdered();
    boolean existsByCategoryId(long categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from EquipmentInventory i where i.equipmentId = :equipmentId")
    Optional<EquipmentInventory> findByEquipmentIdForUpdate(@Param("equipmentId") long equipmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from EquipmentInventory i where i.equipmentId in :ids order by i.equipmentId")
    List<EquipmentInventory> findAllForUpdate(@Param("ids") Collection<Long> ids);
}
