package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.Equipment;
import com.trekking.app.features.tour.enums.EquipmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findAllByStatusNotOrderByNameAscIdAsc(EquipmentStatus status);
    Optional<Equipment> findByIdAndStatusNot(long id, EquipmentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> findForUpdate(@Param("id") long id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, long id);
}
