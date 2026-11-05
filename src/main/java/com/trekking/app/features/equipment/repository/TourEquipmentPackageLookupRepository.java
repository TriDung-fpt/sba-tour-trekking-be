package com.trekking.app.features.equipment.repository;

import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourEquipmentPackageLookupRepository extends JpaRepository<TourEquipmentPackage, Long> {
    Optional<TourEquipmentPackage> findById(long id);
    List<TourEquipmentPackage> findAllByTourIdOrderByIdAsc(long tourId);
    List<TourEquipmentPackage> findAllByTourIdAndActiveTrueOrderByIdAsc(long tourId);
    Optional<TourEquipmentPackage> findFirstByTourIdOrderByIdAsc(long tourId);
    Optional<TourEquipmentPackage> findFirstByTourIdAndActiveTrueOrderByIdAsc(long tourId);
    boolean existsByTourId(long tourId);
    boolean existsByTourIdAndActiveTrue(long tourId);
}
