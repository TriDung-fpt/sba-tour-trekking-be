package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourEquipmentPackageRepository extends JpaRepository<TourEquipmentPackage, Long> {
    Optional<TourEquipmentPackage> findByTourId(long tourId);
    boolean existsByPackageTemplateId(long packageTemplateId);
}
