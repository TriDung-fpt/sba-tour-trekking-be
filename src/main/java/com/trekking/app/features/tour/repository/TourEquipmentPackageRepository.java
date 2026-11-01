package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourEquipmentPackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourEquipmentPackageRepository extends JpaRepository<TourEquipmentPackage, Long> {
    List<TourEquipmentPackage> findAllByTourIdOrderByIdAsc(long tourId);

    List<TourEquipmentPackage> findAllByTourIdAndActiveTrueOrderByIdAsc(long tourId);

    Optional<TourEquipmentPackage> findFirstByTourIdOrderByIdAsc(long tourId);

    Optional<TourEquipmentPackage> findFirstByTourIdAndActiveTrueOrderByIdAsc(long tourId);

    boolean existsByPackageTemplateId(long packageTemplateId);

    @Query(
            value = "select exists(select 1 from trekking.equipment_reservations r where r.package_id = :packageId)",
            nativeQuery = true)
    boolean isReferencedByReservation(@Param("packageId") long packageId);
}
