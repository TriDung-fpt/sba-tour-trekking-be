package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourEquipmentPackageLookupRepository extends JpaRepository<TourEquipmentPackage, Long> {
    Optional<TourEquipmentPackage> findById(long id);
    Optional<TourEquipmentPackage> findByTourId(long tourId);
    boolean existsByTourId(long tourId);
}
