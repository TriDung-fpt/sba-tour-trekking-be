package com.trekking.app.features.tour.repository;

import com.trekking.app.features.tour.entity.TourEquipmentItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TourEquipmentItemRepository extends JpaRepository<TourEquipmentItem, Long> {
    @EntityGraph(attributePaths = "equipment")
    List<TourEquipmentItem> findAllByPackageIdOrderByIdAsc(long packageId);

    @EntityGraph(attributePaths = "equipment")
    List<TourEquipmentItem> findAllByTourIdAndPackageIdIsNullOrderByIdAsc(long tourId);

    @EntityGraph(attributePaths = "equipment")
    List<TourEquipmentItem> findAllByTourIdOrderByIdAsc(long tourId);

    @EntityGraph(attributePaths = "equipment")
    @Query("select i from TourEquipmentItem i join TourEquipmentPackage p on p.id = i.packageId where i.tourId = :tourId and p.active = true order by i.id asc")
    List<TourEquipmentItem> findActivePackageItemsByTourIdOrderByIdAsc(@Param("tourId") long tourId);

    void deleteAllByPackageId(long packageId);

    void deleteAllByTourIdAndPackageIdIsNull(long tourId);

    boolean existsByEquipmentId(long equipmentId);
}
